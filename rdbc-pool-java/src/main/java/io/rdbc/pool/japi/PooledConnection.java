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

import io.github.povder.unipool.japi.PooledResourceHandler;
import io.rdbc.japi.Connection;
import io.rdbc.japi.Statement;
import io.rdbc.japi.StatementOptions;
import io.rdbc.japi.exceptions.UncategorizedRdbcException;
import io.rdbc.japi.util.ThrowingSupplier;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

class PooledConnection implements Connection {

    private final Connection underlying;
    private final PooledResourceHandler<PooledConnection> pool;

    public PooledConnection(Connection underlying, PooledResourceHandler<PooledConnection> pool) {
        this.underlying = underlying;
        this.pool = pool;
    }

    public Connection getUnderlying() {
        return underlying;
    }

    @Override
    public CompletionStage<Void> beginTx(Duration timeout) {
        return underlying.beginTx(timeout);
    }

    @Override
    public CompletionStage<Void> beginTx() {
        return underlying.beginTx();
    }

    @Override
    public CompletionStage<Void> commitTx(Duration timeout) {
        return underlying.commitTx(timeout);
    }

    @Override
    public CompletionStage<Void> commitTx() {
        return underlying.commitTx();
    }

    @Override
    public CompletionStage<Void> rollbackTx(Duration timeout) {
        return underlying.rollbackTx(timeout);
    }

    @Override
    public CompletionStage<Void> rollbackTx() {
        return underlying.rollbackTx();
    }

    @Override
    public <T> CompletionStage<T> withTransaction(ThrowingSupplier<CompletionStage<T>> body) {
        return underlying.withTransaction(body);
    }

    @Override
    public <T> CompletionStage<T> withTransaction(Duration txManageTimeout, ThrowingSupplier<CompletionStage<T>> body) {
        return underlying.withTransaction(txManageTimeout, body);
    }

    @Override
    public CompletionStage<Void> release() {
        return pool.returnResource(this).exceptionally(ex -> {
            throw new UncategorizedRdbcException("Failed to return connection to pool", ex);
        });
    }

    @Override
    public CompletionStage<Void> forceRelease() {
        return pool.destroyResource(this).exceptionally(ex -> {
            throw new UncategorizedRdbcException("Failed to destroy connection in pool", ex);
        });
    }

    @Override
    public CompletionStage<Void> validate(Duration timeout) {
        return underlying.validate(timeout);
    }

    @Override
    public Statement statement(String sql) {
        return underlying.statement(sql);
    }

    @Override
    public Statement statement(String sql, StatementOptions statementOptions) {
        return underlying.statement(sql, statementOptions);
    }

    @Override
    public CompletionStage<Connection> watchForIdle() {
        return underlying.watchForIdle();
    }

    @Override
    public String toString() {
        return "pooled-" + underlying.toString();
    }
}
