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

package io.rdbc.pool.internal.manager

import io.rdbc.pool.RdbcPoolSpec
import io.rdbc.pool.internal.{ConnectionReleaseListener, PendingReqQueue, PendingRequest, PoolConnection}
import io.rdbc.pool.sapi.ConnectionPoolConfig
import io.rdbc.sapi.Connection
import org.scalamock.scalatest.proxy.MockFactory
import org.scalatest.Inside

import scala.concurrent.stm._
import scala.util.Success

class ConnectionManagerSpec
  extends RdbcPoolSpec
    with MockFactory
    with Inside {

  "ConnectionManager" should {
    "report connection deficit correctly" in {
      val size = 10
      val manager = new ConnectionManager(size)
      val active = Set(poolConnMock(), poolConnMock())
      val connecting = 2
      val idle = Set(poolConnMock())
      atomic { implicit tx =>
        manager.active() = active
        manager.connectingCount() = connecting
        manager.idle() = idle
      }

      manager.connectionDeficit() shouldBe (size - active.size - connecting - idle.size)
    }

    "select option of idle connection" when {
      "there are no idle connections" in {
        val manager = new ConnectionManager(5)
        atomic { implicit tx =>
          manager.idle() = Set()
        }
        manager.selectIdleAsActive() shouldBe empty
      }

      "there are idle connections" in {
        val manager = new ConnectionManager(5)
        val idles = Set(poolConnMock(), poolConnMock())
        val actives = Set(poolConnMock())

        atomic { implicit tx =>
          manager.idle() = idles
          manager.active() = actives
        }

        val maybeSelected = manager.selectIdleAsActive()
        maybeSelected should contain oneElementOf idles
        maybeSelected.foreach { selected =>
          atomic { implicit tx =>
            manager.idle() shouldBe (idles - selected)
            manager.active() shouldBe (actives + selected)
          }
        }
      }
    }

    "attempt to decrement connecting count" when {
      "connecting count is positive" in {
        val manager = new ConnectionManager(5)
        val connecting = 2
        atomic { implicit tx =>
          manager.connectingCount() = connecting
        }

        val newConnecting = manager.decrementConnectingCount()
        newConnecting shouldBe Success(connecting - 1)
        atomic { implicit tx =>
          manager.connectingCount() shouldBe (connecting - 1)
        }
      }

      "connecting count is 0" in {
        val manager = new ConnectionManager(5)
        atomic { implicit tx =>
          manager.connectingCount() = 0
        }

        the[IllegalStateException] thrownBy {
          manager.decrementConnectingCount().get
        } should have message "Connecting count is not positive"
      }
    }

    "enqueue requests" when {
      "the queue is empty" in {
        val manager = new ConnectionManager(5)
        val request = new PendingRequest(1)
        manager.enqueueRequest(request)

        inside(manager.queue.single()) { case queue: PendingReqQueue =>
          queue.size shouldBe 1
          queue.contains(request) shouldBe true
        }
      }

      "the queue is non-empty" in {
        val manager = new ConnectionManager(5)
        val oldRequest = new PendingRequest(1)
        atomic { implicit tx =>
          manager.queue() = PendingReqQueue.empty.enqueue(oldRequest)
        }

        val request = new PendingRequest(2)
        manager.enqueueRequest(request)

        inside(manager.queue.single()) { case queue: PendingReqQueue =>
          queue.size shouldBe 2
          queue.contains(oldRequest) shouldBe true
          queue.contains(request) shouldBe true
        }
      }
    }

    "increase connecting count only when in deficit" when {
      "deficit is more than 1" in {
        val manager = new ConnectionManager(2)
        manager.increaseConnectingCountIfAtDeficit() shouldBe 2
        manager.connectingCount.single() shouldBe 1
        manager.connectionDeficit() shouldBe 1
      }

      "deficit is 1" in {
        val manager = new ConnectionManager(1)
        manager.increaseConnectingCountIfAtDeficit() shouldBe 1
        manager.connectingCount.single() shouldBe 1
        manager.connectionDeficit() shouldBe 0
      }

      "deficit is 0" in {
        val manager = new ConnectionManager(1)
        manager.idle.single() = Set(poolConnMock())
        manager.increaseConnectingCountIfAtDeficit() shouldBe 0
        manager.connectingCount.single() shouldBe 0
        manager.connectionDeficit() shouldBe 0
      }
    }

    "attempt to remove active connection" when {
      "connection is in active set" in {
        val manager = new ConnectionManager(5)
        val conn = poolConnMock()
        manager.active.single() = Set(conn)

        manager.removeActive(conn) shouldBe Success(())
        manager.active.single() shouldBe empty
      }

      "connection is not in active set" in {
        val manager = new ConnectionManager(5)
        val conn = poolConnMock()
        val otherConn = poolConnMock()
        manager.active.single() = Set(otherConn)

        assertThrows[IllegalStateException] {
          manager.removeActive(conn).get
        }

        manager.active.single() shouldBe Set(otherConn)
      }
    }
  }

  private def poolConnMock() = {
    new PoolConnection(mock[Connection], ConnectionPoolConfig(), mock[ConnectionReleaseListener])
  }

}
