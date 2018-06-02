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

  object Defaults {
    val Name: String = "unnamed"
    val Size: Int = 20
    val ConnectionValidateTimeout: Timeout = Timeout(5.seconds)
    val ConnectionCreateTimeout: Timeout = Timeout(5.seconds)
    val ConnectionRollbackTimeout: Timeout = Timeout(5.seconds)
    val TaskScheduler: () => TaskScheduler = {
      () => new JdkScheduler(Executors.newSingleThreadScheduledExecutor())(ExecutionContext.global)
    }
    val ExecContext: ExecutionContext = ExecutionContext.global
  }

  def apply(name: String = Defaults.Name,
            size: Int = Defaults.Size,
            connectionValidateTimeout: Timeout = Defaults.ConnectionValidateTimeout,
            connectionCreateTimeout: Timeout = Defaults.ConnectionCreateTimeout,
            connectionRollbackTimeout: Timeout = Defaults.ConnectionRollbackTimeout,
            taskScheduler: () => TaskScheduler = Defaults.TaskScheduler,
            ec: ExecutionContext = Defaults.ExecContext): ConnectionPoolConfig = {

    new ConnectionPoolConfig(
      name = name,
      size = size,
      connectionValidateTimeout = connectionValidateTimeout,
      connectionCreateTimeout = connectionCreateTimeout,
      connectionRollbackTimeout = connectionRollbackTimeout,
      taskSchedulerFactory = taskScheduler,
      executionContext = ec
    )
  }

}

class ConnectionPoolConfig(val name: String,
                           val size: Int,
                           val connectionValidateTimeout: Timeout,
                           val connectionCreateTimeout: Timeout,
                           val connectionRollbackTimeout: Timeout,
                           val taskSchedulerFactory: () => TaskScheduler,
                           val executionContext: ExecutionContext)
