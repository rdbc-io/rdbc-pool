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

package io.rdbc.pool.sapi

import java.util.concurrent.atomic.AtomicLong

import io.rdbc.ImmutSeq
import io.rdbc.api.exceptions.{ConnectionValidationException, IllegalSessionStateException, TimeoutException}
import io.rdbc.implbase.ConnectionFactoryPartialImpl
import io.rdbc.pool.PoolIsShutDownException
import io.rdbc.pool.internal.{PendingReqQueue, PendingRequest, PoolConnection}
import io.rdbc.pool.internal.Compat._
import io.rdbc.sapi.{Connection, ConnectionFactory, Timeout}
import io.rdbc.util.Logging

import scala.concurrent.duration._
import scala.concurrent.stm._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ConnectionPool(connFact: ConnectionFactory, val config: ConnectionPoolConfig)
  extends ConnectionFactory
    with ConnectionFactoryPartialImpl
    with Logging {

  private val queue = Ref(PendingReqQueue.empty)
  private val idle = Ref(Set.empty[PoolConnection])
  private val active = Ref(Set.empty[PoolConnection])
  private val connectingCount = Ref(0)
  private val isShutdown = Ref(false)

  private val taskScheduler = config.taskScheduler()

  private val reqCounter = new AtomicLong(0L)

  protected implicit val ec: ExecutionContext = config.ec

  fillPoolIfAtDeficit()

  def connection()(implicit timeout: Timeout): Future[Connection] = {
    ifNotShutdown {
      val req = new PendingRequest(reqCounter.incrementAndGet(), Promise[PoolConnection])
      atomic { implicit tx =>
        queue() = queue().enqueue(req)
      }

      scheduleTimeout(req, timeout)
      useAtMostOneIdle()

      req.connection
    }
  }

  def shutdown(): Future[Unit] = {
    val doShutdown = atomic { implicit tx =>
      if (!isShutdown()) {
        isShutdown() = true
        true
      } else {
        false
      }
    }
    if (doShutdown) {
      logger.info(s"Shutting down '${config.name}' pool")
      val conns = atomic { implicit tx =>
        val conns = (active() ++ idle()).toVector
        active() = Set.empty
        idle() = Set.empty
        connectingCount() = 0
        conns
      }

      foldShutdownFutures(conns.map(_.underlying.forceRelease()) :+ connFact.shutdown())
        .andThen { case _ =>
          taskScheduler.shutdown()
        }
    } else {
      logger.warn(s"Pool '${config.name}' was already shut down")
      Future.unit
    }
  }

  private[pool] def receiveActiveConnection(conn: PoolConnection): Future[Unit] = {
    ifNotShutdown {
      conn.rollbackTx()(config.rollbackTimeout)
        .flatMap(_ => conn.validate()(config.validateTimeout))
        .map { _ =>
          atomic { implicit tx =>
            dequeuePendingRequest() match {
              case s@Some(_) => s
              case None =>
                removeActive(conn)
                addIdle(conn)
                None
            }
          }.foreach(_.successPromise(conn))
        }
        .recoverWith(handleReceiveConnErrors(conn))
    }
  }

  private[pool] def evictActiveConnection(conn: PoolConnection): Future[Unit] = {
    ifNotShutdown {
      atomic { implicit tx =>
        removeActive(conn)
      }
      conn.underlying.forceRelease()
        .recover {
          case NonFatal(ex) =>
            logWarnException(s"Pool '${config.name}' could not release a connection", ex)
        }
        .andThen { case _ =>
          fillPoolIfAtDeficit()
        }
    }
  }

  private def maybeValidIdle(): Future[Option[PoolConnection]] = {
    val maybeIdle = atomic { implicit tx =>
      val maybeConn = idle().headOption
      maybeConn.foreach { conn =>
        removeIdle(conn)
        addActive(conn)
      }
      maybeConn
    }

    maybeIdle match {
      case Some(conn) =>
        conn.validate()(config.validateTimeout)
          .map(_ => Some(conn))
          .recoverWith {
            case ex: ConnectionValidationException =>
              logWarnException(s"Validation of idle connection failed in pool '${config.name}'", ex)
              evictActiveConnection(conn).transformWith(_ => maybeValidIdle())
          }

      case None => Future.successful(None)
    }
  }

  private def useAtMostOneIdle(): Unit = {
    maybeValidIdle().foreach { maybeConn =>
      maybeConn.foreach { conn =>
        val maybeReq = atomic { implicit tx =>
          val maybeReq = dequeuePendingRequest()
          maybeReq match {
            case s@Some(_) => s
            case None =>
              addIdle(conn)
              None
          }
        }
        maybeReq.foreach(_.successPromise(conn))
      }
    }
  }

  private def scheduleTimeout(req: PendingRequest, timeout: Timeout): Unit = {
    if (timeout.value.isFinite()) {
      val finiteTimeout = FiniteDuration(timeout.value.length, timeout.value.unit)
      val task = taskScheduler.schedule(finiteTimeout) { () =>
        timeoutPendingReq(req, finiteTimeout)
      }
      req.connection.foreach(_ => task.cancel())
    }
  }

  private def acceptNewConnection(conn: PoolConnection): Unit = {
    logger.debug(s"Pool '${config.name}' successfully established a new connection")
    val maybePendingReq = atomic { implicit tx =>
      connectingCount() = connectingCount() - 1
      if (isShutdown()) {
        None
      } else {
        dequeuePendingRequest() match {
          case Some(req) =>
            addActive(conn)
            Some(req)
          case None =>
            addIdle(conn)
            None
        }
      }
    }
    maybePendingReq.foreach(_.successPromise(conn))
  }

  private def increaseConnectingCountIfAtDeficit(): Int = {
    atomic { implicit tx =>
      val deficit = connectionDeficit()
      if (deficit > 0) {
        connectingCount() = connectingCount() + 1
      }
      deficit
    }
  }

  private def openNewConnectionIfAtDeficit(): Future[Option[PoolConnection]] = {
    val deficit = increaseConnectingCountIfAtDeficit()
    if (deficit > 0) {
      connFact.connection()(config.connectTimeout)
        .flatMap { conn =>
          conn.validate()(config.validateTimeout)
            .map(_ => Some(new PoolConnection(conn, this)))
        }
        .andThen {
          case Success(Some(poolConn)) => acceptNewConnection(poolConn)
          case Failure(ex) =>
            logWarnException(s"Pool '${config.name}' could not establish a new connection", ex)
            atomic { implicit tx =>
              connectingCount() = connectingCount() - 1
            }
        }
    } else {
      Future.successful(None)
    }
  }

  private def dequeuePendingRequest()(implicit txn: InTxn): Option[PendingRequest] = {
    queue().dequeueOption.map { case (head, tail) =>
      queue() = tail
      head
    }
  }

  private def handleReceiveConnErrors(conn: PoolConnection): PartialFunction[Throwable, Future[Unit]] = {
    case ex: IllegalSessionStateException =>
      logWarnException(s"Attempted to return to the pool '${config.name} connection in illegal state", ex)
      evictActiveConnection(conn).transformWith(_ => Future.failed(ex))

    case ex: ConnectionValidationException =>
      logWarnException(s"Validation of returned connection failed in pool '${config.name}'", ex)
      evictActiveConnection(conn).recover { case _ => () }

    case NonFatal(ex) =>
      evictActiveConnection(conn).transformWith(_ => Future.failed(ex))
  }

  private def fillPoolIfAtDeficit(): Unit = {
    val deficit = atomic(implicit tx => connectionDeficit())
    if (deficit > 0) {
      (1 to deficit).foreach { _ =>
        openNewConnectionIfAtDeficit()
      }
    }
  }

  private def connectionDeficit()(implicit txn: InTxn): Int = {
    if (isShutdown()) {
      0
    } else {
      config.size - connectingCount() - idle().size - active().size
    }
  }

  private def timeoutPendingReq(req: PendingRequest, timeout: FiniteDuration): Unit = {
    val doTimeout = atomic { implicit tx =>
      if (queue().contains(req)) {
        queue() = queue().evict(req)
        true
      } else false
    }
    if (doTimeout) {
      logger.debug(s"Failing connection request '$req' because of a timeout after $timeout")
      req.failPromise(new TimeoutException(timeout))
    }
  }

  private def foldShutdownFutures(futures: ImmutSeq[Future[Unit]]): Future[Unit] = {
    futures.foldLeft(Future.unit) { (f1, f2) =>
      f1.transformWith {
        case Success(_) => f2
        case Failure(ex) =>
          logWarnException(s"Error occurred during pool '${config.name}' shutdown", ex)
          f2
      }
    }.recover { case _ => () }
    //TODO should the exceptions be ignored here?
  }

  private def ifNotShutdown[A](body: => Future[A]): Future[A] = {
    if (!isShutdown.single()) {
      body
    } else {
      Future.failed(new PoolIsShutDownException(config.name))
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

  private def addIdle(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    addConnToSet(conn, idle, "added to idle set")
  }

  private def removeIdle(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    removeConnFromSet(conn, idle, "removed from idle set")
  }

  private def addActive(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    addConnToSet(conn, active, "added to active set")
  }

  private def removeActive(conn: PoolConnection)(implicit tx: InTxn): Unit = {
    removeConnFromSet(conn, active, "removed from active set")
  }

  private def logWarnException(msg: String, ex: Throwable): Unit = {
    if (logger.underlying.isDebugEnabled) {
      logger.warn(msg, ex)
    } else {
      logger.warn(s"$msg: ${ex.getMessage}")
    }
  }
}
