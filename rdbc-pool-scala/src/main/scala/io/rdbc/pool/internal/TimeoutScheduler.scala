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

package io.rdbc.pool.internal

import io.rdbc.api.exceptions.TimeoutException
import io.rdbc.pool.internal.manager.ConnectionManager
import io.rdbc.sapi.Timeout
import io.rdbc.util.Logging
import io.rdbc.util.scheduler.TaskScheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class TimeoutScheduler(poolManager: ConnectionManager,
                       taskScheduler: TaskScheduler)
                      (implicit ec: ExecutionContext)
  extends Logging {

  def scheduleTimeout(req: PendingRequest, timeout: Timeout): Unit = {
    if (timeout.value.isFinite()) {
      val finiteTimeout = FiniteDuration(timeout.value.length, timeout.value.unit)
      val task = taskScheduler.schedule(finiteTimeout) { () =>
        timeoutPendingReq(req, finiteTimeout)
      }
      req.connection.foreach(_ => task.cancel())
    }
  }

  private def timeoutPendingReq(req: PendingRequest, timeout: FiniteDuration): Unit = {
    val existed = poolManager.evictRequestIfExists(req)
    if (existed) {
      logger.debug(s"Failing connection request '$req' because of a timeout after $timeout")
      req.failPromise(new TimeoutException(timeout))
    }
  }

}
