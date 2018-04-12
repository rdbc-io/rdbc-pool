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

import io.github.povder.unipool.japi.PooledResourceFactory;
import io.github.povder.unipool.japi.PooledResourceHandler;
import io.rdbc.japi.ConnectionFactory;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class PooledConnectionFactory implements PooledResourceFactory<PooledConnection> {

    private final ConnectionFactory connectionFactory;

    public PooledConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public CompletionStage<PooledConnection> createResource(PooledResourceHandler<PooledConnection> pool,
                                                            Duration timeout) {
        return connectionFactory.getConnection(timeout).thenApply(conn ->
                new PooledConnection(conn, pool)
        );
    }

    @Override
    public CompletionStage<Void> shutdown() {
        return connectionFactory.shutdown();
    }
}
