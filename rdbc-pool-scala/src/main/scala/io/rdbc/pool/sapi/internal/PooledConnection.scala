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

import io.github.povder.unipool.sapi.PooledResourceHandler
import io.rdbc.sapi._
import io.rdbc.sapi.exceptions.UncategorizedRdbcException

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

private[pool] class PooledConnection(private[pool] val underlying: Connection,
                                     protected val pool: PooledResourceHandler[PooledConnection])
                                    (implicit ec: ExecutionContext)
  extends Connection {

  def beginTx()(implicit timeout: Timeout): Future[Unit] = {
    underlying.beginTx()
  }

  def commitTx()(implicit timeout: Timeout): Future[Unit] = {
    underlying.commitTx()
  }

  def rollbackTx()(implicit timeout: Timeout): Future[Unit] = {
    underlying.rollbackTx()
  }

  def withTransaction[A](body: => Future[A])(implicit timeout: Timeout): Future[A] = {
    underlying.withTransaction(body)
  }

  def release(): Future[Unit] = {
    pool.returnResource(this).recoverWith {
      case NonFatal(ex) => Future.failed(new UncategorizedRdbcException(
        "Failed to return connection to pool", Some(ex)
      ))
    }
  }

  def forceRelease(): Future[Unit] = {
    pool.destroyResource(this).recoverWith {
      case NonFatal(ex) => Future.failed(new UncategorizedRdbcException(
        "Failed to destroy connection in pool", Some(ex)
      ))
    }
  }

  def validate()(implicit timeout: Timeout): Future[Unit] = {
    underlying.validate()
  }

  def statement(sql: String, statementOptions: StatementOptions): Statement = {
    underlying.statement(sql, statementOptions)
  }

  def statement(sql: String): Statement = {
    underlying.statement(sql)
  }

  def statement(sqlWithParams: SqlWithParams,
                statementOptions: StatementOptions): ExecutableStatement = {
    underlying.statement(sqlWithParams, statementOptions)
  }

  def statement(sqlWithParams: SqlWithParams): ExecutableStatement = {
    underlying.statement(sqlWithParams)
  }

  def watchForIdle: Future[Unit] = {
    underlying.watchForIdle
  }

  override lazy val toString: String = s"pooled-$underlying"

}
