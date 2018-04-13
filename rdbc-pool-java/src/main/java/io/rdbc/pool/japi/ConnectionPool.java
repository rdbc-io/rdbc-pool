/*
 * Copyright 2016 rdbc contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rdbc.pool.japi;

import io.github.povder.unipool.TimeoutException;
import io.github.povder.unipool.japi.Pool;
import io.github.povder.unipool.japi.PoolConfig;
import io.rdbc.japi.Connection;
import io.rdbc.japi.ConnectionFactory;
import io.rdbc.japi.exceptions.UncategorizedRdbcException;
import io.rdbc.japi.util.ThrowingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class ConnectionPool implements ConnectionFactory {

    private final ConnectionPoolConfig config;
    private final Pool<PooledConnection> pool;

    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);

    protected ConnectionPool(ConnectionFactory connectionFactory, ConnectionPoolConfig config) {
        this.config = config;

        PoolConfig unipoolConfig = PoolConfig.builder()
                .name(config.name())
                .size(config.size())
                .borrowTimeout(config.connectionBorrowTimeout())
                .createTimeout(config.connectionCreateTimeout())
                .resetTimeout(config.connectionRollbackTimeout())
                .validateTimeout(config.connectionValidateTimeout())
                .build();

        this.pool = Pool.create(
                new PooledConnectionFactory(connectionFactory),
                new PooledConnectionOps(),
                unipoolConfig);
    }

    public static ConnectionPool create(ConnectionFactory connectionFactory, ConnectionPoolConfig config) {
        return new ConnectionPool(connectionFactory, config);
    }

    public ConnectionPoolConfig getConfig() {
        return config;
    }

    @Override
    public CompletionStage<Connection> getConnection() {
        return pool.borrowResource()
                .thenApply(Connection.class::cast)
                .exceptionally(this::translateBorrowException);
    }

    @Override
    public CompletionStage<Connection> getConnection(Duration timeout) {
        return pool.borrowResource(timeout)
                .thenApply(Connection.class::cast)
                .exceptionally(this::translateBorrowException);
    }

    @Override
    public <T> CompletionStage<T> withConnection(ThrowingFunction<Connection, CompletionStage<T>> body) {
        return withConnection(Duration.ofNanos(Long.MAX_VALUE), body);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> CompletionStage<T> withConnection(Duration connGetTimeout,
                                                 ThrowingFunction<Connection, CompletionStage<T>> body) {
        return getConnection().thenCompose(conn ->
                body.apply(conn)
                        .thenApply(Try::success)
                        .exceptionally(Try::failure)
                        .thenCompose(resultTry ->
                                conn.release().handle((ignoreVoid, releaseEx) -> {
                                    if (releaseEx != null) {
                                        logWarnEx("Ignoring connection release exception", releaseEx);
                                    }
                                    return resultTry;
                                })
                        )
                        .thenCompose(Try::toCompletionStage)
        );
    }

    @Override
    public <T> CompletionStage<T> withTransaction(ThrowingFunction<Connection, CompletionStage<T>> body) {
        return withTransaction(Duration.ofNanos(Long.MAX_VALUE), body);
    }

    @Override
    public <T> CompletionStage<T> withTransaction(Duration timeout,
                                                  ThrowingFunction<Connection, CompletionStage<T>> body) {
        return withConnection(timeout, conn ->
                conn.withTransaction(timeout, () -> body.apply(conn))
        );
    }

    @Override
    public CompletionStage<Void> shutdown() {
        return pool.shutdown().exceptionally(ex -> {
            throw new UncategorizedRdbcException("Shutting down pool failed", ex);
        });
    }

    private Connection translateBorrowException(Throwable t) {
        if (t instanceof TimeoutException) {
            throw new io.rdbc.japi.exceptions.TimeoutException(t.getMessage(), t);
        } else {
            throw new UncategorizedRdbcException("Borrowing connection from pool failed", t);
        }
    }

    private void logWarnEx(String message, Throwable ex) {
        if (log.isDebugEnabled()) {
            log.warn(message, ex);
        } else {
            log.warn(message + ": " + ex.getMessage());
        }
    }

}
