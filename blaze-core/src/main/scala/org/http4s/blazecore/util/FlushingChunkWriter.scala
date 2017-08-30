package org.http4s
package blazecore
package util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1

import scala.concurrent._

import cats.effect.Effect
import fs2._
import org.http4s.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.chunk._
import org.http4s.util.StringWriter

private[http4s] class FlushingChunkWriter[F[_]](pipe: TailStage[ByteBuffer],
  trailer: F[Headers])(implicit protected val F: Effect[F],
    protected val ec: ExecutionContext)
    extends Http1Writer[F] {
  import ChunkWriter._

  protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    if (chunk.isEmpty) FutureUnit
    else pipe.channelWrite(encodeChunk(chunk, Nil))
  }

  protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    {
      if (!chunk.isEmpty) writeBodyChunk(chunk, true).flatMap { _ => writeTrailer(pipe, trailer) }
      else writeTrailer(pipe, trailer)
    }.map(_ => false)

  override def writeHeaders(headerWriter: StringWriter): Future[Unit] =
    // It may be a while before we get another chunk, so we flush now
    pipe.channelWrite(List(Http1Writer.headersToByteBuffer(headerWriter.result), TransferEncodingChunked))
}
