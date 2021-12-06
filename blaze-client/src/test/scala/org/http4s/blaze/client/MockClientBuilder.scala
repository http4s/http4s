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
package blaze
package client

import cats.effect.IO
import org.http4s.blaze.pipeline.HeadStage
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.client.ConnectionBuilder

import java.nio.ByteBuffer

private[client] object MockClientBuilder {
  def builder(
      head: => HeadStage[ByteBuffer],
      tail: => BlazeConnection[IO],
  ): ConnectionBuilder[IO, BlazeConnection[IO]] = { _ =>
    IO {
      val t = tail
      LeafBuilder(t).base(head)
      t
    }
  }

  def manager(
      head: => HeadStage[ByteBuffer],
      tail: => BlazeConnection[IO],
  ): ConnectionManager[IO, BlazeConnection[IO]] =
    ConnectionManager.basic(builder(head, tail))
}
