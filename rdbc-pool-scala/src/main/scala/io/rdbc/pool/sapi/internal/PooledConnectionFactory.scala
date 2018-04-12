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

import io.github.povder.unipool.sapi.{PooledResourceFactory, PooledResourceHandler, Timeout}
import io.rdbc.pool.sapi.internal.Conversions._
import io.rdbc.sapi.ConnectionFactory

import scala.concurrent.{ExecutionContext, Future}

private[pool] class PooledConnectionFactory(connectionFactory: ConnectionFactory)
                                           (implicit ec: ExecutionContext)
  extends PooledResourceFactory[PooledConnection] {

  def createResource(pool: PooledResourceHandler[PooledConnection],
                     timeout: Timeout): Future[PooledConnection] = {
    connectionFactory.connection()(timeout.toRdbc).map { conn =>
      new PooledConnection(conn, pool)
    }
  }

  def shutdown(): Future[Unit] = connectionFactory.shutdown()
}
