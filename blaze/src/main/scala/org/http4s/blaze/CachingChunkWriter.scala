package org.http4s
package blaze

import org.http4s.BodyChunk
import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.TailStage
import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.slf4j.Logging


/**
 * Created by Bryce Anderson on 4/12/14.
 */
class CachingChunkWriter(headers: ByteBuffer,
                         pipe: TailStage[ByteBuffer],
                         bufferSize: Int = 10*1024)(implicit ec: ExecutionContext)
              extends ChunkProcessWriter(headers, pipe) {

  private var bodyBuffer: BodyChunk = null

  private def addChunk(b: BodyChunk): BodyChunk = {
    if (bodyBuffer == null) bodyBuffer = b
    else bodyBuffer = bodyBuffer ++ b

    bodyBuffer
  }

  override protected def exceptionFlush(): Future[Any] = {
    val c = bodyBuffer
    bodyBuffer = null
    if (c != null && c.length > 0) super.writeBodyChunk(c, true)  // TODO: would we want to writeEnd?
    else Future.successful()
  }

  override protected def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] = {
    val b = addChunk(chunk)
    bodyBuffer = null
    super.writeEnd(b, t)
  }

  override protected def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any] = {
    val c = addChunk(chunk)
    if (c.length >= bufferSize || flush) { // time to flush
      bodyBuffer = null
      super.writeBodyChunk(c, true)
    }
    else Future.successful()    // Pretend to be done.
  }
}
