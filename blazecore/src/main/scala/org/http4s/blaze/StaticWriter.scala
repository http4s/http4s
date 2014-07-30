package org.http4s
package blaze

import java.nio.ByteBuffer
import org.http4s.blaze.util.ProcessWriter
import pipeline.TailStage
import scala.concurrent.{ExecutionContext, Future}
import scodec.bits.ByteVector
import com.typesafe.scalalogging.slf4j.LazyLogging

class StaticWriter(private var buffer: ByteBuffer, size: Int, out: TailStage[ByteBuffer])
                  (implicit val ec: ExecutionContext)
                              extends ProcessWriter with LazyLogging {

  private var written = 0

  private def checkWritten(): Unit = if (size > 0 && written > size) {
    logger.warn(s"Expected $size bytes, $written written")
  }

  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = {
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

  protected def writeEnd(chunk: ByteVector): Future[Unit] = writeBodyChunk(chunk, true)
}
