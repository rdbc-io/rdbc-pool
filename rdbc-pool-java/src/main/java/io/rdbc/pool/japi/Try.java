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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class Try<T> {
    private final T success;
    private final Throwable failure;

    private Try(T success, Throwable failure) {
        this.success = success;
        this.failure = failure;
    }

    static <T> Try<T> success(T value) {
        return new Try<>(value, null);
    }

    static <T> Try<T> failure(Throwable ex) {
        return new Try<>(null, ex);
    }

    CompletionStage<T> toCompletionStage() {
        CompletableFuture<T> fut = new CompletableFuture<>();
        if (failure != null) {
            fut.completeExceptionally(failure);
        } else {
            fut.complete(success);
        }
        return fut;
    }

}
