/*
 * Copyright 2017 Krzysztof Pado
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

package io.rdbc.pool

import java.util.concurrent.Executors

import io.rdbc.sapi.Timeout
import io.rdbc.util.scheduler.{JdkScheduler, TaskScheduler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ConnectionPoolConfig {
  def Default: ConnectionPoolConfig = {
    ConnectionPoolConfig(
      name = "unnamed",
      ec = ExecutionContext.global,
      validateTimeout = Timeout(5.seconds),
      connectTimeout = Timeout(5.seconds),
      rollbackTimeout = Timeout(5.seconds),
      taskScheduler = new JdkScheduler(Executors.newSingleThreadScheduledExecutor()), //TODO oooooo
      size = 20
    )
  }
}

case class ConnectionPoolConfig(name: String,
                                size: Int,
                                validateTimeout: Timeout,
                                connectTimeout: Timeout,
                                rollbackTimeout: Timeout,
                                taskScheduler: TaskScheduler,
                                ec: ExecutionContext)
