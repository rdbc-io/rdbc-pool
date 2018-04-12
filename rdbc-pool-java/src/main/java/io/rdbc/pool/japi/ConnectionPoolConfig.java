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


import org.immutables.value.Value.Default;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

import static io.rdbc.pool.japi.PoolConfigDefaults.UNIPOOL_DEFAULTS;
import static org.immutables.value.Value.Style.ImplementationVisibility.PACKAGE;

@Immutable
@Style(visibility = PACKAGE, typeImmutable = "ImmutableConnectionPoolConfig")
public interface ConnectionPoolConfig {

    @Default
    default String name() {
        return UNIPOOL_DEFAULTS.name();
    }

    @Default
    default int size() {
        return UNIPOOL_DEFAULTS.size();
    }

    @Default
    default Duration connectionValidateTimeout() {
        return UNIPOOL_DEFAULTS.validateTimeout();
    }

    @Default
    default Duration connectionBorrowTimeout() {
        return UNIPOOL_DEFAULTS.borrowTimeout();
    }

    @Default
    default Duration connectionCreateTimeout() {
        return UNIPOOL_DEFAULTS.createTimeout();
    }

    @Default
    default Duration connectionRollbackTimeout() {
        return UNIPOOL_DEFAULTS.resetTimeout();
    }

    @Default
    default ExecutorService executorService() {
        return UNIPOOL_DEFAULTS.executorService();
    }

    static Builder builder() {
        return ImmutableConnectionPoolConfig.builder();
    }

    interface Builder {
        Builder name(String name);

        Builder size(int size);

        Builder connectionBorrowTimeout(Duration borrowTimeout);

        Builder connectionValidateTimeout(Duration validateTimeout);

        Builder connectionCreateTimeout(Duration createTimeout);

        Builder connectionRollbackTimeout(Duration rollbackTimeout);

        Builder executorService(ExecutorService executorService);

        ConnectionPoolConfig build();
    }
}
