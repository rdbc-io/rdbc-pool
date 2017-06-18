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

package io.rdbc.pool.internal

import scala.collection.immutable.TreeSet

private[pool]
sealed trait PendingReqQueue {
  def contains(req: PendingRequest): Boolean

  def evict(req: PendingRequest): PendingReqQueue

  def enqueue(req: PendingRequest): PendingReqQueue

  def dequeueOption: Option[(PendingRequest, PendingReqQueue)]

  def size: Int

  def isEmpty: Boolean
}

private[pool] object PendingReqQueue {
  val empty: PendingReqQueue = Empty

  private object Empty extends PendingReqQueue {
    def contains(req: PendingRequest): Boolean = false

    def evict(req: PendingRequest): PendingReqQueue = this

    def enqueue(req: PendingRequest): PendingReqQueue = {
      new NonEmpty(TreeSet(req)(Ordering.by(_.id)))
    }

    val dequeueOption: Option[(PendingRequest, PendingReqQueue)] = None

    val isEmpty = true

    val size = 0

    override val toString: String = "PendingReqQueue()"
  }

  private class NonEmpty(private val set: TreeSet[PendingRequest])
    extends PendingReqQueue {

    def contains(req: PendingRequest): Boolean = {
      set.contains(req)
    }

    def evict(req: PendingRequest): PendingReqQueue = {
      val newSet = set - req
      if (newSet.isEmpty) {
        PendingReqQueue.empty
      } else {
        new NonEmpty(newSet)
      }
    }

    def enqueue(req: PendingRequest): PendingReqQueue = {
      new NonEmpty(set + req)
    }

    def dequeueOption: Option[(PendingRequest, PendingReqQueue)] = {
      set.headOption.map { req =>
        val newSet = set - req
        val newQueue = if (newSet.isEmpty) {
          PendingReqQueue.empty
        } else {
          new NonEmpty(newSet)
        }
        (req, newQueue)
      }
    }

    val isEmpty = false

    val size: Int = set.size

    override lazy val toString: String = {
      set.mkString("PendingReqQueue(", ",", ")")
    }
  }

}
