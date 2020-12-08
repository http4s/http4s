/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
