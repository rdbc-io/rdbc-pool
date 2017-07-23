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

import io.rdbc.pool.internal.{ConnectionReleaseListener, PendingRequest, PoolConnection}
import io.rdbc.pool.sapi.ConnectionPoolConfig
import io.rdbc.sapi.Connection
import org.scalamock.scalatest.proxy.MockFactory

import scala.concurrent.ExecutionContext.Implicits.global

class PendingRequestSpec extends RdbcPoolSpec with MockFactory {

  "A pending request" should {
    "Complete connection future successfully" in {
      val req = new PendingRequest(1L)
      val conn = poolConnMock()
      req.connection.isCompleted shouldBe false
      req.success(conn)
      req.connection.isCompleted shouldBe true
      req.connection.foreach { futureConn =>
        futureConn should be theSameInstanceAs conn
      }
    }

    "Complete connection future with a failure" in {
      val req = new PendingRequest(1L)
      val ex = new RuntimeException
      req.connection.isCompleted shouldBe false
      req.failure(ex)
      req.connection.isCompleted shouldBe true
      req.connection.failed.foreach { futureEx =>
        futureEx should be theSameInstanceAs ex
      }
    }
  }

  private def poolConnMock() = {
    new PoolConnection(mock[Connection], ConnectionPoolConfig(), mock[ConnectionReleaseListener])
  }

}
