/*
 * Copyright 2014 http4s.org
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

package org.http4s
package client

import cats.effect._
import cats.syntax.all._

private final class BasicManager[F[_], A <: Connection[F]](builder: ConnectionBuilder[F, A])(
    implicit F: Sync[F])
    extends ConnectionManager[F, A] {
  def borrow(requestKey: RequestKey): F[NextConnection] =
    builder(requestKey).map(NextConnection(_, fresh = true))

  override def shutdown: F[Unit] =
    F.unit

  override def invalidate(connection: A): F[Unit] =
    F.delay(connection.shutdown())

  override def release(connection: A): F[Unit] =
    invalidate(connection)
}
