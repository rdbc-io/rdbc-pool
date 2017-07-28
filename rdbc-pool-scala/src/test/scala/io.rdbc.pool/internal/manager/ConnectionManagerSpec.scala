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

import io.rdbc.pool.internal.{PendingReqQueue, PendingRequest, PoolConnection}
import io.rdbc.pool.{MockableConnReleaseListener, RdbcPoolSpec}
import io.rdbc.sapi.Connection
import org.scalamock.scalatest.MockFactory
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

    "accept returned active connections" when {
      "there are requests waiting" in {
        val manager = new ConnectionManager(5)
        val request = new PendingRequest(1L)
        val conn = poolConnMock()

        manager.active.single() = Set(conn)
        manager.queue.single() = PendingReqQueue.empty.enqueue(request)

        val maybeReqTry = manager.selectRequestOrAddActiveToIdle(conn)
        maybeReqTry shouldBe a[Success[_]]

        maybeReqTry.foreach { maybeReq =>
          maybeReq shouldBe defined
          maybeReq.foreach { returnedReq =>
            returnedReq shouldBe theSameInstanceAs(request)
          }
        }

        inside(manager.queue.single()) { case queue: PendingReqQueue =>
          queue.isEmpty shouldBe true
        }

        manager.active.single() should contain only conn
      }

      "there are no requests waiting" in {
        val manager = new ConnectionManager(5)
        val conn = poolConnMock()
        manager.active.single() = Set(conn)

        manager.selectRequestOrAddActiveToIdle(conn) shouldBe Success(None)
        manager.active.single() shouldBe empty
        manager.idle.single() should contain only conn
      }
    }

    "not accept returned not active connections" in {
      val manager = new ConnectionManager(5)
      val activeConn = poolConnMock()
      val idleConn = poolConnMock()
      manager.active.single() = Set(activeConn)
      manager.idle.single() = Set(idleConn)

      assertThrows[IllegalStateException] {
        manager.selectRequestOrAddActiveToIdle(poolConnMock()).get
      }
      manager.active.single() should contain only activeConn
      manager.idle.single() should contain only idleConn
    }

    "clear connections" when {
      "there are connections to clear" in {
        val manager = new ConnectionManager(5)

        val conns = Set(poolConnMock(), poolConnMock(), poolConnMock(), poolConnMock())

        manager.idle.single() = conns.slice(0, 2)
        manager.active.single() = conns.slice(2, 4)
        manager.connectingCount.single() = 2

        manager.clearConnections() shouldBe conns
        manager.idle.single() shouldBe empty
        manager.active.single() shouldBe empty
        manager.connectingCount.single() shouldBe 0
      }

      "there are no connections" in {
        val manager = new ConnectionManager(5)

        manager.clearConnections() shouldBe empty
        manager.idle.single() shouldBe empty
        manager.active.single() shouldBe empty
        manager.connectingCount.single() shouldBe 0
      }
    }

    "evict requests" when {
      "the request is still pending" in {
        val manager = new ConnectionManager(5)
        val req = new PendingRequest(1)
        val otherReq = new PendingRequest(2)

        manager.queue.single() = PendingReqQueue.empty.enqueue(req).enqueue(otherReq)

        manager.evictRequestIfExists(req) shouldBe true
        inside(manager.queue.single()) { case queue: PendingReqQueue =>
          queue.contains(otherReq) shouldBe true
          queue.contains(req) shouldBe false
          queue.size shouldBe 1
        }
      }

      "the request is not pending anymore" in {
        val manager = new ConnectionManager(5)
        val otherReq = new PendingRequest(2)

        manager.queue.single() = PendingReqQueue.empty.enqueue(otherReq)

        manager.evictRequestIfExists(new PendingRequest(1)) shouldBe false
        inside(manager.queue.single()) { case queue: PendingReqQueue =>
          queue.contains(otherReq) shouldBe true
          queue.size shouldBe 1
        }
      }
    }

    "fail on accepting new connection" when {
      "the connection is already in idle set" in {
        val manager = new ConnectionManager(5)
        val conn = poolConnMock()
        manager.idle.single() = Set(conn)
        manager.connectingCount.single() = 1

        assertThrows[IllegalStateException] {
          manager.selectRequestOrAddNewToIdle(conn).get
        }
      }

      "the connection is already in active set" in {
        val manager = new ConnectionManager(5)
        val conn = poolConnMock()
        manager.active.single() = Set(conn)
        manager.connectingCount.single() = 1

        assertThrows[IllegalStateException] {
          manager.selectRequestOrAddNewToIdle(conn).get
        }
      }

      "the connecting count is not positive" in {
        val manager = new ConnectionManager(5)
        val conn = poolConnMock()
        manager.connectingCount.single() = 0

        assertThrows[IllegalStateException] {
          manager.selectRequestOrAddNewToIdle(conn).get
        }
      }
    }

    "accept new connection" when {
      "there is a pending request" in {
        val manager = new ConnectionManager(5)
        val request = new PendingRequest(1)
        manager.connectingCount.single() = 1
        manager.queue.single() = PendingReqQueue.empty.enqueue(request)

        val conn = poolConnMock()
        val maybeReqTry = manager.selectRequestOrAddNewToIdle(conn)

        maybeReqTry shouldBe a[Success[_]]
        maybeReqTry.foreach { maybeReq =>
          maybeReq shouldBe defined
          maybeReq.foreach { returnedReq =>
            returnedReq shouldBe theSameInstanceAs(request)
          }
        }
        manager.active.single() should contain only conn
        manager.idle.single() shouldBe empty
        manager.queue.single().isEmpty shouldBe true
      }

      "there are no pending requests" in {
        val manager = new ConnectionManager(5)
        manager.connectingCount.single() = 1

        val conn = poolConnMock()
        val maybeReqTry = manager.selectRequestOrAddNewToIdle(conn)

        maybeReqTry shouldBe a[Success[_]]
        maybeReqTry.foreach { maybeReq =>
          maybeReq shouldBe empty
        }
        manager.active.single() shouldBe empty
        manager.idle.single() should contain only conn
      }
    }
  }

  private def poolConnMock() = {
    new PoolConnection(mock[Connection], "pool", mock[MockableConnReleaseListener])
  }

}
