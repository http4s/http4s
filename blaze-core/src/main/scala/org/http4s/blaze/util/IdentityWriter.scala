package org.http4s.blaze.util

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.TailStage
import org.log4s.getLogger
import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future}

class IdentityWriter(private var headers: ByteBuffer, size: Long, out: TailStage[ByteBuffer])
                    (implicit val ec: ExecutionContext)
    extends ProcessWriter {

  private[this] val logger = getLogger

  private var bodyBytesWritten = 0

  private def willOverflow(count: Int) =
    if (size < 0) false else (count + bodyBytesWritten > size)

  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] =
    if (willOverflow(chunk.size)) {
      // never write past what we have promised using the Content-Length header
      val msg = s"Will not write more bytes than what was indicated by the Content-Length header ($size)"

      logger.warn(msg)

      writeBodyChunk(chunk.take((size - bodyBytesWritten).toInt), true) flatMap {_ =>
        Future.failed(new IllegalArgumentException(msg))
      }

    }
    else {
      val b = chunk.toByteBuffer

      bodyBytesWritten += b.remaining

      if (headers != null) {
        val h = headers
        headers = null
        out.channelWrite(h::b::Nil)
      }
      else out.channelWrite(b)
    }

  protected def writeEnd(chunk: ByteVector): Future[Boolean] = {
    val total = bodyBytesWritten + chunk.size

    if (size < 0 || total >= size) writeBodyChunk(chunk, flush = true).
      map(Function.const(size < 0)) // require close if infinite
    else {
      val msg = s"Expected `Content-Length: $size` bytes, but only $total were written."

      logger.warn(msg)

      writeBodyChunk(chunk, flush = true) flatMap {_ =>
        Future.failed(new IllegalStateException(msg))
      }
    }
  }
}
