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

import cats.effect.Async
import fs2._
import java.nio.ByteBuffer

import cats.effect.std.Dispatcher
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

import scala.concurrent._

private[http4s] class FlushingChunkWriter[F[_]](pipe: TailStage[ByteBuffer], trailer: F[Headers])(
    implicit
    protected val F: Async[F],
    protected val ec: ExecutionContext,
    protected val D: Dispatcher[F])
    extends Http1Writer[F] {
  import ChunkWriter._

  protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    if (chunk.isEmpty) FutureUnit
    else pipe.channelWrite(encodeChunk(chunk, Nil))

  protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    if (!chunk.isEmpty) writeBodyChunk(chunk, true).flatMap { _ =>
      writeTrailer(pipe, trailer)
    }
    else writeTrailer(pipe, trailer)
  }.map(_ => false)

  override def writeHeaders(headerWriter: StringWriter): Future[Unit] =
    // It may be a while before we get another chunk, so we flush now
    pipe.channelWrite(
      List(Http1Writer.headersToByteBuffer(headerWriter.result), TransferEncodingChunked))
}
