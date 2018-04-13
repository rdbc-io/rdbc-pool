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

import io.github.povder.unipool.{TimeoutException => UnipoolTimeoutException}
import io.github.povder.unipool.sapi.Pool
import io.rdbc.implbase.ConnectionFactoryPartialImpl
import io.rdbc.pool.sapi.internal.Conversions._
import io.rdbc.pool.sapi.internal.{PooledConnectionFactory, PooledConnectionOps}
import io.rdbc.sapi.exceptions.{TimeoutException, UncategorizedRdbcException}
import io.rdbc.sapi.{Connection, ConnectionFactory, Timeout}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object ConnectionPool {
  def apply(connectionFactory: ConnectionFactory,
            config: ConnectionPoolConfig): ConnectionPool = {
    new ConnectionPool(connectionFactory, config)
  }
}

class ConnectionPool protected(connectionFactory: ConnectionFactory, val config: ConnectionPoolConfig)
  extends ConnectionFactory
    with ConnectionFactoryPartialImpl {

  protected implicit val ec: ExecutionContext = config.executionContext

  private val pool = Pool(
    resourceFact = new PooledConnectionFactory(connectionFactory),
    resourceOps = PooledConnectionOps,
    config = config.toUnipool
  )

  def connection()(implicit timeout: Timeout): Future[Connection] = {
    pool.borrowResource(timeout.toUnipool).recoverWith {
      case ex: UnipoolTimeoutException => Future.failed(
        new TimeoutException(timeout, Some(ex))
      )

      case NonFatal(ex) => Future.failed(new UncategorizedRdbcException(
        "Borrowing connection from pool failed", Some(ex)
      ))
    }
  }

  def shutdown(): Future[Unit] = {
    pool.shutdown().recoverWith {
      case NonFatal(ex) => Future.failed(new UncategorizedRdbcException(
        "Shutting down pool failed", Some(ex)
      ))
    }
  }

}
