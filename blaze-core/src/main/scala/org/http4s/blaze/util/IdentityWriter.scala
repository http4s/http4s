package org.http4s.blaze.util

import java.nio.ByteBuffer

import scala.concurrent.{ExecutionContext, Future}

import fs2._
import org.http4s.batteries._
import org.http4s.blaze.pipeline.TailStage
import org.log4s.getLogger

class IdentityWriter(private var headers: ByteBuffer, size: Long, out: TailStage[ByteBuffer])
                    (implicit val ec: ExecutionContext)
    extends EntityBodyWriter {

  private[this] val logger = getLogger

  private var bodyBytesWritten = 0L

  private def willOverflow(count: Long) =
    if (size < 0L) false else (count + bodyBytesWritten > size)

  protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    if (willOverflow(chunk.size.toLong)) {
      // never write past what we have promised using the Content-Length header
      val msg = s"Will not write more bytes than what was indicated by the Content-Length header ($size)"

      logger.warn(msg)

      // TODO fs2 port shady .toInt... loop?
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

  protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
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
