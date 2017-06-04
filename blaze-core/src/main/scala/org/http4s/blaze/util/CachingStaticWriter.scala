package org.http4s.blaze.util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import cats.effect._
import fs2._
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

import scala.concurrent.{ExecutionContext, Future}

class CachingStaticWriter[F[_]](writer: StringWriter, out: TailStage[ByteBuffer],
                                bufferSize: Int = 8*1024)
                               (implicit protected val F: Effect[F],
                                protected val ec: ExecutionContext)
  extends EntityBodyWriter[F] {

  @volatile
  private var _forceClose = false
  private var bodyBuffer: Chunk[Byte] = _
  private var innerWriter: InnerWriter = _

  private def addChunk(b: Chunk[Byte]): Chunk[Byte] = {
    if (bodyBuffer == null) bodyBuffer = b
    else bodyBuffer = bodyBuffer.toBytes.concatAll(Seq(b))
    bodyBuffer
  }

  override protected def exceptionFlush(): Future[Unit] = {
    val c = bodyBuffer
    bodyBuffer = null

    if (innerWriter == null) {  // We haven't written anything yet
      writer << "\r\n"
      val b = ByteBuffer.wrap(writer.result.getBytes(StandardCharsets.ISO_8859_1))
      new InnerWriter(b).writeBodyChunk(c, flush = true)
    }
    else writeBodyChunk(c, flush = true)    // we are already proceeding
  }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    if (innerWriter != null) innerWriter.writeEnd(chunk)
    else {  // We are finished! Write the length and the keep alive
      val c = addChunk(chunk)
      writer << "Content-Length: " << c.size << "\r\nConnection: keep-alive\r\n\r\n"

      val b = ByteBuffer.wrap(writer.result.getBytes(StandardCharsets.ISO_8859_1))

      new InnerWriter(b).writeEnd(c).map(_ || _forceClose)
    }
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    if (innerWriter != null) innerWriter.writeBodyChunk(chunk, flush)
    else {
      val c = addChunk(chunk)
      if (flush || c.size >= bufferSize) { // time to just abort and stream it
        _forceClose = true
        writer << "\r\n"
        val b = ByteBuffer.wrap(writer.result.getBytes(StandardCharsets.ISO_8859_1))
        innerWriter = new InnerWriter(b)
        innerWriter.writeBodyChunk(chunk, flush)
      }
      else Future.unit
    }
  }

  // Make the write stuff public
  private class InnerWriter(buffer: ByteBuffer) extends IdentityWriter(buffer, -1, out) {
    override def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = super.writeEnd(chunk)
    override def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = super.writeBodyChunk(chunk, flush)
  }
}
