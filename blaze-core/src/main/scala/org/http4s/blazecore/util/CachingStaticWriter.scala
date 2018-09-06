package org.http4s
package blazecore
package util

import cats.effect._
import fs2._
import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter
import scala.collection.mutable.Buffer
import scala.concurrent.{ExecutionContext, Future}

private[http4s] class CachingStaticWriter[F[_]](
    out: TailStage[ByteBuffer],
    bufferSize: Int = 8 * 1024)(
    implicit protected val F: Effect[F],
    protected val ec: ExecutionContext)
    extends Http1Writer[F] {

  @volatile
  private var _forceClose = false
  private val bodyBuffer: Buffer[Chunk[Byte]] = Buffer()
  private var writer: StringWriter = null
  private var innerWriter: InnerWriter = _

  def writeHeaders(headerWriter: StringWriter): Future[Unit] = {
    this.writer = headerWriter
    FutureUnit
  }

  private def addChunk(chunk: Chunk[Byte]): Unit = {
    bodyBuffer += chunk
    ()
  }

  private def toChunk: Chunk[Byte] = Chunk.concatBytes(bodyBuffer.toSeq)

  private def clear(): Unit = bodyBuffer.clear()

  override protected def exceptionFlush(): Future[Unit] = {
    val c = toChunk
    clear()

    if (innerWriter == null) { // We haven't written anything yet
      writer << "\r\n"
      new InnerWriter().writeBodyChunk(c, flush = true)
    } else writeBodyChunk(c, flush = true) // we are already proceeding
  }

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] =
    if (innerWriter != null) innerWriter.writeEnd(chunk)
    else { // We are finished! Write the length and the keep alive
      addChunk(chunk)
      val c = toChunk
      clear()
      writer << "Content-Length: " << c.size << "\r\nConnection: keep-alive\r\n\r\n"

      new InnerWriter().writeEnd(c).map(_ || _forceClose)
    }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    if (innerWriter != null) innerWriter.writeBodyChunk(chunk, flush)
    else {
      addChunk(chunk)
      val c = toChunk
      if (flush || c.size >= bufferSize) { // time to just abort and stream it
        _forceClose = true
        writer << "\r\n"
        innerWriter = new InnerWriter
        innerWriter.writeBodyChunk(chunk, flush)
      } else FutureUnit
    }

  // Make the write stuff public
  private class InnerWriter extends IdentityWriter(-1, out) {
    override def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = super.writeEnd(chunk)
    override def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
      super.writeBodyChunk(chunk, flush)
  }
}
