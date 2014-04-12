package org.http4s.blaze

import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.TailStage
import scala.concurrent.{Future, ExecutionContext}
import org.http4s.{TrailerChunk, BodyChunk}
import org.http4s.util.StringWriter
import com.typesafe.scalalogging.slf4j.Logging
import java.nio.charset.StandardCharsets
import org.http4s.Header.`Content-Length`

/**
 * Created by Bryce Anderson on 4/12/14.
 */
class CachingStaticWriter(writer: StringWriter, out: TailStage[ByteBuffer], bufferSize: Int = 8*1024)
                              (implicit val ec: ExecutionContext)
        extends ProcessWriter with Logging {

  private var bodyBuffer: BodyChunk = null

  private var _forceClose = false

  override def forceClose(): Boolean = _forceClose

  private def addChunk(b: BodyChunk): BodyChunk = {
    if (bodyBuffer == null) bodyBuffer = b
    else bodyBuffer = bodyBuffer ++ b

    bodyBuffer
  }

  private var innerWriter: InnerWriter = null

  override protected def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] = {
    if (innerWriter != null) innerWriter.writeEnd(chunk, t)
    else {  // We are finished! Write the length and the keep alive
      val c = addChunk(chunk)
      writer ~ `Content-Length`(c.length) ~ "\r\nConnection:Keep-Alive\r\n\r\n"

      val b = ByteBuffer.wrap(writer.result().getBytes(StandardCharsets.US_ASCII))

      new InnerWriter(b).writeEnd(c, t)
    }
  }


  override protected def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any] = {

    if (innerWriter != null) innerWriter.writeBodyChunk(chunk, flush)
    else {
      val c = addChunk(chunk)
      if (c.length >= bufferSize) { // time to just abort and stream it
        _forceClose = true
        writer ~ '\r' ~ '\n'
        val b = ByteBuffer.wrap(writer.result().getBytes(StandardCharsets.US_ASCII))
        innerWriter = new InnerWriter(b)
        innerWriter.writeBodyChunk(chunk, flush)
      }
      else Future.successful()
    }

  }

  // Make the write stuff public
  private class InnerWriter(buffer: ByteBuffer) extends StaticWriter(buffer, -1, out) {

    override def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] = super.writeEnd(chunk, t)

    override def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any] = super.writeBodyChunk(chunk, flush)
  }
}
