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

import java.util.concurrent.atomic.AtomicBoolean

import io.rdbc.api.exceptions.{ConnectionValidationException, IllegalSessionStateException}
import io.rdbc.implbase.ConnectionFactoryPartialImpl
import io.rdbc.pool.internal.manager.ConnectionManager
import io.rdbc.pool.internal.{ConnectionReleaseListener, PendingRequest, PoolConnection}
import io.rdbc.pool.{PoolInactiveException, PoolInternalErrorException}
import io.rdbc.sapi.{Connection, ConnectionFactory, Timeout}
import io.rdbc.util.Logging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import io.rdbc.pool.internal.Compat._
import io.rdbc.pool.internal.scheduler.TimeoutScheduler

object ConnectionPool {
  def apply(connFact: ConnectionFactory, config: ConnectionPoolConfig): ConnectionPool = {
    new ConnectionPool(connFact, config)
  }
}

class ConnectionPool protected(connFact: ConnectionFactory, val config: ConnectionPoolConfig)
  extends ConnectionFactory
    with ConnectionFactoryPartialImpl
    with ConnectionReleaseListener
    with Logging {

  protected implicit val ec: ExecutionContext = config.executionContext

  private val connManager = new ConnectionManager(config.size)
  private val taskScheduler = config.taskSchedulerFactory()
  private val timeoutScheduler = new TimeoutScheduler(connManager, taskScheduler)

  private val _active = new AtomicBoolean(true)

  fillPoolIfAtDeficit()

  def connection()(implicit timeout: Timeout): Future[Connection] = {
    ifActive {
      val req = new PendingRequest(System.nanoTime())
      connManager.enqueueRequest(req)

      timeoutScheduler.scheduleTimeout(req, timeout)
      useAtMostOneIdle()

      req.connection
    }
  }

  def shutdown(): Future[Unit] = {
    val doShutdown = _active.compareAndSet(true, false)
    if (doShutdown) {
      logger.info(s"Shutting down '${config.name}' pool")
      val conns = connManager.clearConnections()

      foldShutdownFutures(conns.map(_.underlying.forceRelease()) + connFact.shutdown())
        .andThen { case _ =>
          taskScheduler.shutdown()
        }
    } else {
      logger.warn(s"Pool '${config.name}' was already shut down")
      Future.unit
    }
  }

  def active: Boolean = _active.get()

  private[pool] def activeConnectionReleased(conn: PoolConnection): Future[Unit] = {
    ifActive {
      conn.rollbackTx()(config.rollbackTimeout)
        .flatMap(_ => conn.validate()(config.validateTimeout))
        .flatMap { _ =>
          Future.fromTry(connManager.selectRequestOrAddActiveToIdle(conn))
            .map { maybePendingReq =>
              maybePendingReq.foreach(_.success(conn))
            }
        }
        .recoverWith(handleReceiveConnErrors(conn))
    }
  }

  private[pool] def activeConnectionForceReleased(conn: PoolConnection): Future[Unit] = {
    ifActive {
      Future.fromTry(connManager.removeActive(conn)).flatMap { _ =>
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
  }

  private def maybeValidIdle(): Future[Option[PoolConnection]] = {
    val maybeIdle = connManager.selectIdleAsActive()

    maybeIdle match {
      case Some(conn) =>
        conn.validate()(config.validateTimeout)
          .map(_ => Some(conn))
          .recoverWith {
            case ex: ConnectionValidationException =>
              logWarnException(s"Validation of idle connection failed in pool '${config.name}'", ex)
              activeConnectionForceReleased(conn).transformWith(_ => maybeValidIdle())
          }

      case None => Future.successful(None)
    }
  }

  private def useAtMostOneIdle(): Unit = {
    maybeValidIdle().foreach { maybeConn =>
      maybeConn.foreach { conn =>
        connManager.selectRequestOrAddActiveToIdle(conn).map { maybeReq =>
          maybeReq.foreach(_.success(conn))
        }
      }
    }
  }

  private def acceptNewConnection(conn: PoolConnection): Try[Unit] = {
    logger.debug(s"Pool '${config.name}' successfully established a new connection")
    connManager.selectRequestOrAddNewToIdle(conn).map { maybePendingReq =>
      maybePendingReq.foreach(_.success(conn))
    }
  }

  private def openNewConnectionIfAtDeficit(): Future[Option[PoolConnection]] = {
    val deficit = connManager.increaseConnectingCountIfAtDeficit()
    if (deficit > 0) {
      connFact.connection()(config.connectTimeout)
        .flatMap { conn =>
          conn.validate()(config.validateTimeout)
            .map(_ => Some(new PoolConnection(conn, config.name, this)))
        }
        .transformWith {
          case Success(Some(poolConn)) =>
            Future.fromTry(acceptNewConnection(poolConn)).map { _ =>
              Some(poolConn)
            }

          case Failure(ex) =>
            logWarnException(s"Pool '${config.name}' could not establish a new connection", ex)
            connManager.decrementConnectingCount() match {
              case Success(_) => Future.failed(ex)
              case Failure(decrementEx) =>
                val internalError = new PoolInternalErrorException(
                  "Attempted to decrement connecting count but it was already non-positive",
                  decrementEx)
                logger.error(internalError.getMessage, internalError)
                Future.failed(internalError)
            }
        }
    } else {
      Future.successful(None)
    }
  }

  private def handleReceiveConnErrors(conn: PoolConnection): PartialFunction[Throwable, Future[Unit]] = {
    case ex: IllegalSessionStateException =>
      logWarnException(s"Attempted to return to the pool '${config.name} connection in illegal state", ex)
      activeConnectionForceReleased(conn).transformWith(_ => Future.failed(ex))

    case ex: ConnectionValidationException =>
      logWarnException(s"Validation of returned connection failed in pool '${config.name}'", ex)
      activeConnectionForceReleased(conn).recover { case _ => () }

    case NonFatal(ex) =>
      activeConnectionForceReleased(conn).transformWith(_ => Future.failed(ex))
  }

  private def fillPoolIfAtDeficit(): Unit = {
    val deficit = connectionDeficit()
    if (deficit > 0) {
      (1 to deficit).foreach { _ =>
        openNewConnectionIfAtDeficit()
      }
    }
  }

  private def connectionDeficit(): Int = {
    if (!_active.get()) {
      0
    } else {
      connManager.connectionDeficit()
    }
  }

  private def foldShutdownFutures(futures: Set[Future[Unit]]): Future[Unit] = {
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

  private def ifActive[A](body: => Future[A]): Future[A] = {
    if (_active.get()) {
      body
    } else {
      Future.failed(new PoolInactiveException(config.name))
    }
  }

  private def logWarnException(msg: String, ex: Throwable): Unit = {
    if (logger.underlying.isDebugEnabled) {
      logger.warn(msg, ex)
    } else {
      logger.warn(s"$msg: ${ex.getMessage}")
    }
  }
}
