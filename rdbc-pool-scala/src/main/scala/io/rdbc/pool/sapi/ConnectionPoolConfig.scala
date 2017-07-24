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

package io.rdbc.pool.sapi

import java.util.concurrent.Executors

import io.rdbc.sapi.Timeout
import io.rdbc.util.scheduler.{JdkScheduler, TaskScheduler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ConnectionPoolConfig {

  def apply(name: String = "unnamed",
            size: Int = 20,
            validateTimeout: Timeout = Timeout(5.seconds),
            connectTimeout: Timeout = Timeout(5.seconds),
            rollbackTimeout: Timeout = Timeout(5.seconds),
            taskScheduler: () => TaskScheduler = {
              () => new JdkScheduler(Executors.newSingleThreadScheduledExecutor())(ExecutionContext.global)
            },
            ec: ExecutionContext = ExecutionContext.global): ConnectionPoolConfig = {

    new ConnectionPoolConfig(
      name = name,
      size = size,
      validateTimeout = validateTimeout,
      connectTimeout = connectTimeout,
      rollbackTimeout = rollbackTimeout,
      taskSchedulerFactory = taskScheduler,
      executionContext = ec
    )
  }

}

class ConnectionPoolConfig(val name: String,
                           val size: Int,
                           val validateTimeout: Timeout,
                           val connectTimeout: Timeout,
                           val rollbackTimeout: Timeout,
                           val taskSchedulerFactory: () => TaskScheduler,
                           val executionContext: ExecutionContext)
