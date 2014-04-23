package org.http4s
package blaze

import java.nio.ByteBuffer
import pipeline.TailStage
import org.http4s.util.Logging
import scala.concurrent.{ExecutionContext, Future}
import scodec.bits.ByteVector

/**
 * @author Bryce Anderson
 *         Created on 1/10/14
 */
class StaticWriter(private var buffer: ByteBuffer, size: Int, out: TailStage[ByteBuffer])
                  (implicit val ec: ExecutionContext)
                              extends ProcessWriter with Logging {

  private var written = 0

  private def checkWritten(): Unit = if (size > 0 && written > size) {
    logger.warn(s"Expected $size bytes, $written written")
  }

  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Any] = {
    val b = chunk.toByteBuffer
    written += b.remaining()
    checkWritten()

    if (buffer != null) {
      val i = buffer
      buffer = null
      out.channelWrite(i::b::Nil)
    }
    else out.channelWrite(b)
  }

  protected def writeEnd(chunk: ByteVector): Future[Any] = writeBodyChunk(chunk, true)
}
