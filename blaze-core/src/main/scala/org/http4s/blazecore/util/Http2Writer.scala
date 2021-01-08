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
package blazecore
package util

import cats.effect._
import fs2._
import org.http4s.blaze.http.Headers
import org.http4s.blaze.http.http2.{DataFrame, HeadersFrame, Priority, StreamFrame}
import org.http4s.blaze.pipeline.TailStage
import scala.concurrent._

private[http4s] class Http2Writer[F[_]](
    tail: TailStage[StreamFrame],
    private var headers: Headers,
    protected val ec: ExecutionContext)(implicit protected val F: Async[F])
    extends EntityBodyWriter[F] {
  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    val f =
      if (headers == null) tail.channelWrite(DataFrame(endStream = true, chunk.toByteBuffer))
      else {
        val hs = headers
        headers = null
        if (chunk.isEmpty)
          tail.channelWrite(HeadersFrame(Priority.NoPriority, endStream = true, hs))
        else
          tail.channelWrite(
            HeadersFrame(Priority.NoPriority, endStream = false, hs)
              :: DataFrame(endStream = true, chunk.toByteBuffer)
              :: Nil)
      }

    f.map(Function.const(false))(ec)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    if (chunk.isEmpty) FutureUnit
    else if (headers == null) tail.channelWrite(DataFrame(endStream = false, chunk.toByteBuffer))
    else {
      val hs = headers
      headers = null
      tail.channelWrite(
        HeadersFrame(Priority.NoPriority, endStream = false, hs)
          :: DataFrame(endStream = false, chunk.toByteBuffer)
          :: Nil)
    }
}
