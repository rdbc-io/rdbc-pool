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
    new PoolConnection(mock[Connection], ConnectionPoolConfig.Default, mock[ConnectionReleaseListener])
  }

}
