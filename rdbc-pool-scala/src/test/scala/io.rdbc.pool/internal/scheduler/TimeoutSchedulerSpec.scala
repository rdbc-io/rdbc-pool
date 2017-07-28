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

package io.rdbc.pool.internal.scheduler

import java.util.concurrent.Executors

import io.rdbc.api.exceptions.TimeoutException
import io.rdbc.pool.RdbcPoolSpec
import io.rdbc.pool.internal.manager.ConnectionManager
import io.rdbc.pool.internal.{PendingRequest, PoolConnection}
import io.rdbc.sapi.Timeout
import io.rdbc.util.scheduler.{JdkScheduler, TaskScheduler}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TimeoutSchedulerSpec
  extends RdbcPoolSpec
    with MockFactory {

  private implicit val ec = ExecutionContext.global

  "TimeoutScheduler" should {

    "fail requests if processing time is exceeded" in {
      val timeoutDuration = 1.second
      val timeout = Timeout(timeoutDuration)
      val request = new PendingRequest(1L)
      val taskScheduler = new JdkScheduler(Executors.newSingleThreadScheduledExecutor())
      val connManager = mock[ConnectionManager]

      (connManager.evictRequestIfExists _).expects(request).returning(true)

      val timeoutScheduler = new TimeoutScheduler(connManager, taskScheduler)

      timeoutScheduler.scheduleTimeout(request, timeout)

      //wait for the timeout to happen
      Thread.sleep((timeoutDuration + 1.second).toMillis)

      request.connection.value shouldBe defined
      request.connection.value.foreach { connVal =>
        connVal shouldBe a[Failure[_]]
        connVal.failed.foreach { ex =>
          ex shouldBe a[TimeoutException]
        }
      }
    }

    "don't fail requests if processing time is not exceeded" in {
      val timeoutDuration = 2.seconds
      val timeout = Timeout(timeoutDuration)
      val request = new PendingRequest(1L)
      val taskScheduler = new JdkScheduler(Executors.newSingleThreadScheduledExecutor())
      val connManager = mock[ConnectionManager]
      val returnedConn = mock[PoolConnection]

      val timeoutScheduler = new TimeoutScheduler(connManager, taskScheduler)

      timeoutScheduler.scheduleTimeout(request, timeout)

      Thread.sleep((timeoutDuration - 1.second).toMillis)
      request.success(returnedConn)

      //wait for the time to reach the timeout
      Thread.sleep((timeoutDuration + 1.second).toMillis)

      //check whether the timeout scheduler didn't fail the request
      request.connection.value shouldBe defined
      request.connection.value.foreach { connVal =>
        connVal shouldBe a[Success[_]]
        connVal.foreach { returnedConn =>
          returnedConn should be theSameInstanceAs returnedConn
        }
      }
    }

    "don't schedule timeouts for infinite timeout values" in {
      val taskScheduler = mock[TaskScheduler]

      (taskScheduler.schedule(_: FiniteDuration)(_: (() => Unit))).expects(*, *).never()

      val timeoutScheduler = new TimeoutScheduler(mock[ConnectionManager], mock[TaskScheduler])
      timeoutScheduler.scheduleTimeout(mock[PendingRequest], Timeout.Inf)
    }

    "timeout pending request" when {
      "request was still pending" in {
        val req = mock[PendingRequest]
        val manager = mock[ConnectionManager]
        (manager.evictRequestIfExists _).expects(req).once().returning(true)
        (req.failure _).expects(where { ex: Throwable =>
          ex.isInstanceOf[TimeoutException]
        }).once().returning(())

        val scheduler = new TimeoutScheduler(manager, mock[TaskScheduler])
        scheduler.timeoutPendingReq(req, 5.seconds)
      }

      "request was not pending anymore" in {
        val req = mock[PendingRequest]
        val manager = mock[ConnectionManager]
        (manager.evictRequestIfExists _).expects(req).once().returning(false)
        (req.failure _).expects(*).never()

        val scheduler = new TimeoutScheduler(manager, mock[TaskScheduler])
        scheduler.timeoutPendingReq(req, 5.seconds)
      }
    }
  }
}
