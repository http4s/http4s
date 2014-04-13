package org.http4s.blaze

import java.nio.ByteBuffer
import pipeline.TailStage
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s.{TrailerChunk, BodyChunk}
import scala.concurrent.{ExecutionContext, Future}

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

  protected def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any] = {
    val b = chunk.asByteBuffer
    written += b.remaining()
    checkWritten()

    if (buffer != null) {
      val i = buffer
      buffer = null
      out.channelWrite(i::b::Nil)
    }
    else out.channelWrite(b)
  }

  protected def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] = {
    if (t.isDefined) {
      logger.warn(s"Trailer '${t.get}'found for defined length content. Ignoring.")
    }

    writeBodyChunk(chunk, true)
  }
}
