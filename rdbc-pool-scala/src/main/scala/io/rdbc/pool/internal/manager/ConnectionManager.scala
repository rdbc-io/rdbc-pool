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
import io.rdbc.util.Logging

import scala.concurrent.stm.{InTxn, Ref, Txn, atomic}

private[pool] class ConnectionManager(poolSize: Int)
  extends Logging {

  private[manager] val queue = Ref(PendingReqQueue.empty)
  private[manager] val idle = Ref(Set.empty[PoolConnection])
  private[manager] val active = Ref(Set.empty[PoolConnection])
  private[manager] val connectingCount = Ref(0)

  def removeActive(conn: PoolConnection): Unit = {
    atomic { implicit tx =>
      removeActiveInternal(conn)
    }
  }

  def selectRequestOrAddActiveToIdle(conn: PoolConnection): Option[PendingRequest] = {
    atomic { implicit tx =>
      dequeuePendingRequest() match {
        case s@Some(_) => s
        case None =>
          removeActiveInternal(conn)
          addIdleInternal(conn)
          None
      }
    }
  }

  def selectRequestOrAddNewToIdle(conn: PoolConnection): Option[PendingRequest] = {
    atomic { implicit tx =>
      decrementConnectingCount()
      dequeuePendingRequest() match {
        case Some(req) =>
          addActiveInternal(conn)
          Some(req)
        case None =>
          addIdleInternal(conn)
          None
      }
    }
  }

  def increaseConnectingCountIfAtDeficit(): Int = {
    atomic { implicit tx =>
      val deficit = connectionDeficit()
      if (deficit > 0) {
        incrementConnectingCount()
      }
      deficit
    }
  }

  def decrementConnectingCount(): Int = {
    atomic { implicit tx =>
      decrementConnectingCountInternal()
    }
  }

  def evictRequestIfExists(req: PendingRequest): Boolean = {
    atomic { implicit tx =>
      if (queue().contains(req)) {
        queue() = queue().evict(req)
        true
      } else false
    }
  }

  def enqueueRequest(req: PendingRequest): Unit = {
    atomic { implicit tx =>
      queue() = queue().enqueue(req)
    }
  }

  def clearConnections(): Vector[PoolConnection] = {
    atomic { implicit tx =>
      val conns = (active() ++ idle()).toVector
      active() = Set.empty
      idle() = Set.empty
      connectingCount() = 0
      conns
    }
  }

  def selectIdleAsActive(): Option[PoolConnection] = {
    atomic { implicit tx =>
      val maybeConn = idle().headOption
      maybeConn.foreach { conn =>
        removeIdleInternal(conn)
        addActiveInternal(conn)
      }
      maybeConn
    }
  }

  def connectionDeficit(): Int = {
    atomic { implicit tx =>
      poolSize - connectingCount() - idle().size - active().size
    }
  }

  private def dequeuePendingRequest()(implicit txn: InTxn): Option[PendingRequest] = {
    queue().dequeueOption.map { case (head, tail) =>
      queue() = tail
      head
    }
  }

  private def addConnToSet(conn: PoolConnection,
                           coll: Ref[Set[PoolConnection]],
                           msg: String)
                          (implicit tx: InTxn): Unit = {
    coll() = coll() + conn
    Txn.afterCommit { _ =>
      logger.debug(s"$conn $msg")
    }
  }

  private def removeConnFromSet(conn: PoolConnection,
                                coll: Ref[Set[PoolConnection]],
                                msg: String)
                               (implicit tx: InTxn): Unit = {
    coll() = coll() - conn
    Txn.afterCommit { _ =>
      logger.debug(s"$conn $msg")
    }
  }

  private def addIdleInternal(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    addConnToSet(conn, idle, "added to idle set")
  }

  private def removeIdleInternal(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    removeConnFromSet(conn, idle, "removed from idle set")
  }

  private def addActiveInternal(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    addConnToSet(conn, active, "added to active set")
  }

  private def removeActiveInternal(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    removeConnFromSet(conn, active, "removed from active set")
  }

  private def incrementConnectingCount()(implicit tx: InTxn): Int = {
    connectingCount() = connectingCount() + 1
    connectingCount()
  }

  private def decrementConnectingCountInternal()(implicit tx: InTxn): Int = {
    connectingCount() = connectingCount() - 1
    connectingCount()
  }

}
