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

package io.rdbc.pool.sapi.internal

import io.github.povder.unipool.sapi.scheduler.{
  ScheduledTask => UnipoolScheduledTask,
  TaskScheduler => UnipoolTaskScheduler
}
import io.github.povder.unipool.sapi.{PoolConfig, Timeout => PTimeout}
import io.rdbc.pool.sapi.ConnectionPoolConfig
import io.rdbc.sapi.Timeout
import io.rdbc.util.scheduler.{ScheduledTask => RdbcScheduledTask, TaskScheduler => RdbcTaskScheduler}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[pool] object Conversions {

  implicit class PoolTimeoutToRdbcTimeout(val poolTimeout: PTimeout) {

    def toRdbc: Timeout = {
      Timeout(poolTimeout.value)
    }
  }

  implicit class RdbcTimeoutToPoolTimeout(val rdbcTimeout: Timeout) {

    def toUnipool: PTimeout = {
      PTimeout(rdbcTimeout.value)
    }
  }

  implicit class ConnPoolConfigToUnipoolConfig(val connPoolConfig: ConnectionPoolConfig)
    extends AnyVal {

    def toUnipool: PoolConfig = {
      PoolConfig(
        name = connPoolConfig.name,
        size = connPoolConfig.size,
        borrowTimeout = connPoolConfig.connectionBorrowTimeout.toUnipool,
        validateTimeout = connPoolConfig.connectionValidateTimeout.toUnipool,
        createTimeout = connPoolConfig.connectionCreateTimeout.toUnipool,
        resetTimeout = connPoolConfig.connectionRollbackTimeout.toUnipool,
        taskScheduler = () => connPoolConfig.taskSchedulerFactory().toUnipool,
        ec = connPoolConfig.executionContext
      )
    }

  }

  implicit class RdbcTaskSchedulerToUnipool(val rdbcTaskScheduler: RdbcTaskScheduler)
    extends AnyVal {

    def toUnipool: UnipoolTaskScheduler = {
      new UnipoolTaskScheduler {

        def schedule(delay: FiniteDuration)(action: () => Unit): UnipoolScheduledTask = {
          rdbcTaskScheduler.schedule(delay)(action).toUnipool
        }

        def shutdown(): Future[Unit] = {
          rdbcTaskScheduler.shutdown()
        }
      }
    }

  }

  implicit class RdbcScheduledTaskToUnipool(val rdbcScheduledTask: RdbcScheduledTask) {

    def toUnipool: UnipoolScheduledTask = {
      new UnipoolScheduledTask {
        def cancel(): Unit = rdbcScheduledTask.cancel()
      }
    }
  }

}
