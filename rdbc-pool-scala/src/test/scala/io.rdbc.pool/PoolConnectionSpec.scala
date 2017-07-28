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

package io.rdbc.pool

import io.rdbc.pool.internal.PoolConnection
import io.rdbc.sapi._
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Future
import scala.concurrent.duration._

import io.rdbc.pool.internal.Compat._

class PoolConnectionSpec
  extends RdbcPoolSpec
    with MockFactory {

  private val poolName = "pool"

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

    "notify about a connection release" in {
      val listener = mock[MockableConnReleaseListener]
      val conn = poolConn(listener)
      val res = Future.unit
      (listener.connReleased(_: PoolConnection)).expects(conn).once().returning(res)
      conn.release() shouldBe theSameInstanceAs(res)
    }

    "notify about a connection force release" in {
      val listener = mock[MockableConnReleaseListener]
      val conn = poolConn(listener)
      val res = Future.unit
      (listener.connForceReleased(_: PoolConnection)).expects(conn).once().returning(res)
      conn.forceRelease() shouldBe theSameInstanceAs(res)
    }
  }

  private def poolConn(connReleaseListener: MockableConnReleaseListener): PoolConnection = {
    new PoolConnection(mock[Connection], poolName, connReleaseListener)
  }

  private def poolConn(): PoolConnection = {
    poolConn(mock[MockableConnReleaseListener])
  }

}
