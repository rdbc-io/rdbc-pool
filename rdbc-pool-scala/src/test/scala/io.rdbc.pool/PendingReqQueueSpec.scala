package io.rdbc.pool

import io.rdbc.pool.internal.{PendingReqQueue, PendingRequest, PoolConnection}

import scala.concurrent.Promise

class PendingReqQueueSpec extends RdbcPoolSpec {

  "A request queue" when {
    "empty" should {
      "return None when dequeue is invoked" in {
        PendingReqQueue.empty.dequeueOption shouldBe empty
      }

      "return true for isEmpty" in {
        PendingReqQueue.empty.isEmpty shouldBe true
      }

      "have size equal to 0" in {
        PendingReqQueue.empty.size shouldBe 0
      }

      "return empty queue when evict is invoked" in {
        PendingReqQueue.empty.evict(pendingReq).isEmpty shouldBe true
      }

      "return false for contains check" in {
        PendingReqQueue.empty.contains(pendingReq) shouldBe false
      }

      "produce a non-empty queue" in {
        PendingReqQueue.empty.enqueue(pendingReq).isEmpty shouldBe false
      }

      "ignore an eviction attempts" in {
        PendingReqQueue.empty.evict(pendingReq).isEmpty shouldBe true
      }

      "have a correct string representation" in {
        PendingReqQueue.empty.toString shouldBe "PendingReqQueue()"
      }
    }
  }

  "A request queue" should {
    "order requests by ID when dequeueing" in {
      val req1 = pendingReq(1L)
      val req2 = pendingReq(2L)
      val req3 = pendingReq(3L)
      val q = PendingReqQueue.empty
        .enqueue(req2)
        .enqueue(req1)
        .enqueue(req3)

      q.dequeueOption shouldBe defined
      q.dequeueOption.foreach { case (r1, q2) =>
        r1 shouldBe req1
        q2.dequeueOption shouldBe defined
        q2.dequeueOption.foreach { case (r2, q3) =>
          r2 shouldBe req2
          q3.dequeueOption shouldBe defined
          q3.dequeueOption.foreach { case (r3, q4) =>
            r3 shouldBe req3
            q4.isEmpty shouldBe true
            q4.dequeueOption shouldBe empty
          }
        }
      }
    }

    "be able to find enqueued item" in {
      PendingReqQueue.empty.enqueue(pendingReq).contains(pendingReq) shouldBe true
    }

    "should evict existing item" in {
      val q = PendingReqQueue.empty.enqueue(pendingReq)
      val emptyQueue = q.evict(pendingReq)
      emptyQueue.isEmpty shouldBe true
      emptyQueue.size shouldBe 0
      emptyQueue.dequeueOption shouldBe empty
    }

    "should report size correctly" in {
      val q = PendingReqQueue.empty.enqueue(pendingReq(1L))
      q.size shouldBe 1
      q.enqueue(pendingReq(2L)).size shouldBe 2
    }

    "should report empty status correctly" in {
      val q = PendingReqQueue.empty.enqueue(pendingReq(1L))
      q.isEmpty shouldBe false
    }

    "should ignore existing elements when enqueueing" in {
      val q = PendingReqQueue.empty.enqueue(pendingReq)
      q.enqueue(pendingReq).size shouldBe 1

    }

    "should ignore non-existing elements when evicting" in {
      val req = pendingReq(1L)
      val q = PendingReqQueue.empty.enqueue(req)
      val newQ = q.evict(pendingReq(2L))
      newQ.size shouldBe 1
      val deq = newQ.dequeueOption
      deq shouldBe defined
      deq.foreach { case (deqReq, deqQueue) =>
        deqReq should be theSameInstanceAs req
        deqQueue.isEmpty shouldBe true
      }
    }
  }

  val pendingReq: PendingRequest = {
    pendingReq(1L)
  }

  def pendingReq(id: Long): PendingRequest = {
    new PendingRequest(id, Promise[PoolConnection])
  }
}
