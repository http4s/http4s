package org.http4s
package blazecore
package util

import cats.effect._
import fs2._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter
import org.http4s.syntax.byteChunk._
import scala.concurrent._

private[http4s] class CachingChunkWriter[F[_]](
    pipe: TailStage[ByteBuffer],
    trailer: F[Headers],
    bufferMaxSize: Int = 10 * 1024)(
    implicit protected val F: Effect[F],
    protected val ec: ExecutionContext)
    extends Http1Writer[F] {
  import ChunkWriter._

  private[this] var pendingHeaders: StringWriter = _
  private[this] var bodyBuffer: Chunk[Byte] = _

  override def writeHeaders(headerWriter: StringWriter): Future[Unit] = {
    pendingHeaders = headerWriter
    FutureUnit
  }

  private def addChunk(b: Chunk[Byte]): Chunk[Byte] = {
    if (bodyBuffer == null) bodyBuffer = b
    else bodyBuffer = (bodyBuffer.toSegment ++ b.toSegment).force.toChunk
    bodyBuffer
  }

  override protected def exceptionFlush(): Future[Unit] = {
    val c = bodyBuffer
    bodyBuffer = null
    if (c != null && !c.isEmpty) pipe.channelWrite(encodeChunk(c, Nil))
    else FutureUnit
  }

  def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    val b = addChunk(chunk)
    bodyBuffer = null
    doWriteEnd(b)
  }

  private def doWriteEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    val f =
      if (pendingHeaders != null) { // This is the first write, so we can add a body length instead of chunking
        val h = pendingHeaders
        pendingHeaders = null

        if (!chunk.isEmpty) {
          val body = chunk.toByteBuffer
          h << s"Content-Length: ${body.remaining()}\r\n\r\n"

          // Trailers are optional, so dropping because we have no body.
          val hbuff = ByteBuffer.wrap(h.result.getBytes(ISO_8859_1))
          pipe.channelWrite(hbuff :: body :: Nil)
        } else {
          h << s"Content-Length: 0\r\n\r\n"
          val hbuff = ByteBuffer.wrap(h.result.getBytes(ISO_8859_1))
          pipe.channelWrite(hbuff)
        }
      } else {
        if (!chunk.isEmpty) writeBodyChunk(chunk, true).flatMap { _ =>
          writeTrailer(pipe, trailer)
        } else writeTrailer(pipe, trailer)
      }

    f.map(Function.const(false))
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    val c = addChunk(chunk)
    if (c.size >= bufferMaxSize || flush) { // time to flush
      bodyBuffer = null
      pipe.channelWrite(encodeChunk(c, Nil))
    } else FutureUnit // Pretend to be done.
  }

  private def encodeChunk(chunk: Chunk[Byte], last: List[ByteBuffer]): List[ByteBuffer] = {
    val list = ChunkWriter.encodeChunk(chunk, last)
    if (pendingHeaders != null) {
      pendingHeaders << TransferEncodingChunkedString
      val b = ByteBuffer.wrap(pendingHeaders.result.getBytes(ISO_8859_1))
      pendingHeaders = null
      b :: list
    } else list
  }
}
