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
import io.rdbc.pool.sapi.internal.Compat._
import io.rdbc.pool.sapi.RdbcPoolSpec
import io.rdbc.sapi._
import org.scalamock.scalatest.MockFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class PooledConnectionSpec
  extends RdbcPoolSpec
    with MockFactory {

  private implicit val ec = ExecutionContext.global
  private implicit val timeout = Timeout(10.seconds)

  "PoolConnection" should {
    "forward beginTx calls" in {
      val conn = poolConn()
      val timeout = Timeout(5.seconds)
      val res = Future.unit
      (conn.underlying.beginTx()(_: Timeout)).expects(timeout).once().returning(res)
      conn.beginTx()(timeout) shouldBe theSameInstanceAs(res)
    }

    "forward rollbackTx calls" in {
      val conn = poolConn()
      val timeout = Timeout(5.seconds)
      val res = Future.unit
      (conn.underlying.rollbackTx()(_: Timeout)).expects(timeout).once().returning(res)
      conn.rollbackTx()(timeout) shouldBe theSameInstanceAs(res)
    }

    "forward commitTx calls" in {
      val conn = poolConn()
      val timeout = Timeout(5.seconds)
      val res = Future.unit
      (conn.underlying.commitTx()(_: Timeout)).expects(timeout).once().returning(res)
      conn.commitTx()(timeout) shouldBe theSameInstanceAs(res)
    }

    "forward validate calls" in {
      val conn = poolConn()
      val timeout = Timeout(5.seconds)
      val res = Future.unit
      (conn.underlying.validate()(_: Timeout)).expects(timeout).once().returning(res)
      conn.validate()(timeout) shouldBe theSameInstanceAs(res)
    }

    "forward statement(sql, options) calls" in {
      val conn = poolConn()
      val res = mock[Statement]
      val sql = "select 1"
      val options = StatementOptions.Default
      (conn.underlying.statement(_: String, _: StatementOptions))
        .expects(sql, options).once().returning(res)
      conn.statement(sql, options) shouldBe theSameInstanceAs(res)
    }

    "forward statement(sql) calls" in {
      val conn = poolConn()
      val res = mock[Statement]
      val sql = "select 1"
      (conn.underlying.statement(_: String))
        .expects(sql).once().returning(res)
      conn.statement(sql) shouldBe theSameInstanceAs(res)
    }

    "forward statement(sqlWithParams, options) calls" in {
      val conn = poolConn()
      val res = mock[ExecutableStatement]
      val sql = sql"select 1"
      val options = StatementOptions.Default
      (conn.underlying.statement(_: SqlWithParams, _: StatementOptions))
        .expects(sql, options).once().returning(res)
      conn.statement(sql, options) shouldBe theSameInstanceAs(res)
    }

    "forward statement(sqlWithParams) calls" in {
      val conn = poolConn()
      val res = mock[ExecutableStatement]
      val sql = sql"select 1"
      (conn.underlying.statement(_: SqlWithParams))
        .expects(sql).once().returning(res)
      conn.statement(sql) shouldBe theSameInstanceAs(res)
    }

    "forward watchForIdle calls" in {
      val conn = poolConn()
      val res = Future.unit
      (conn.underlying.watchForIdle _).expects().once().returning(res)
      conn.watchForIdle shouldBe theSameInstanceAs(res)
    }

    "not fail when requested as a string" in {
      val conn = poolConn()
      noException should be thrownBy conn.toString
    }

    /* can't currently mock withTransaction method
    "forward withTransaction calls" in {
      val conn = poolConn()
      val timeout = Timeout(5.seconds)
      val res = Future.unit
      val body = Future.unit
      (conn.underlying.withTransaction(_ : Future[Unit])(_: Timeout)).expects(body, timeout).once().returning(res)
      conn.withTransaction(body)(timeout) shouldBe theSameInstanceAs(res)
    }
    */

    "return itself to pool on release" in {
      val handler = mock[PooledResourceHandler[PooledConnection]]
      val conn = poolConn(handler)
      val res = Future.unit
      (handler.returnResource(_: PooledConnection)).expects(conn).once().returning(res)
      conn.release().get shouldBe(())
    }

    "destroy itself on force release" in {
      val handler = mock[PooledResourceHandler[PooledConnection]]
      val conn = poolConn(handler)
      val res = Future.unit
      (handler.destroyResource(_: PooledConnection)).expects(conn).once().returning(res)
      conn.forceRelease().get shouldBe(())
    }
  }

  private def poolConn(handler: PooledResourceHandler[PooledConnection]): PooledConnection = {
    new PooledConnection(mock[Connection], handler)
  }

  private def poolConn(): PooledConnection = {
    poolConn(mock[PooledResourceHandler[PooledConnection]])
  }

}
