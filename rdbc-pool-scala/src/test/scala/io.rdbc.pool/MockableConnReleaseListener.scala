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

import io.rdbc.pool.internal.{ConnectionReleaseListener, PoolConnection}

import scala.concurrent.Future

trait MockableConnReleaseListener extends ConnectionReleaseListener {
  private[pool] def activeConnectionReleased(conn: PoolConnection): Future[Unit] = {
    connReleased(conn)
  }

  private[pool] def activeConnectionForceReleased(conn: PoolConnection): Future[Unit] = {
    connForceReleased(conn)
  }

  def connReleased(conn: PoolConnection): Future[Unit]
  def connForceReleased(conn: PoolConnection): Future[Unit]
}
