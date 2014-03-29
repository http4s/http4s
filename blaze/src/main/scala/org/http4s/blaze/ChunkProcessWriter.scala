package org.http4s.blaze

import java.nio.ByteBuffer
import pipeline.TailStage
import scala.concurrent.{Future, ExecutionContext}
import org.http4s.{TrailerChunk, BodyChunk}
import java.nio.charset.Charset
import org.http4s.util.StringWriter

/**
 * @author Bryce Anderson
 *         Created on 1/10/14
 */
final class ChunkProcessWriter(private var headers: ByteBuffer, pipe: TailStage[ByteBuffer])
                              (implicit val ec: ExecutionContext) extends ProcessWriter {

  import ChunkProcessWriter._

  private def CRLF = ByteBuffer.wrap(CRLFBytes).asReadOnlyBuffer()

  private val lengthBuffer = ByteBuffer.allocate(15)    // Should be enough



  protected def writeBodyChunk(chunk: BodyChunk, flush: Boolean): Future[Any] = {
    pipe.channelWrite(encodeChunk(chunk, Nil))
  }


  protected def writeEnd(chunk: BodyChunk, t: Option[TrailerChunk]): Future[Any] = {

    val tailbuffer = t match {
      case Some(t) =>
        val rr = new StringWriter(256)
        rr ~ '0' ~ '\r' ~ '\n'             // Last chunk
        t.headers.foreach( h =>  rr ~ h.name.toString ~ ": " ~ h ~ '\r' ~ '\n')   // trailers
        rr ~ '\r' ~ '\n'          // end of chunks
        ByteBuffer.wrap(rr.result().getBytes(ASCII))

      case None => ByteBuffer.wrap(ChunkEndBytes)
    }

    val all = if (!chunk.isEmpty) encodeChunk(chunk, tailbuffer::Nil) 
              else if (headers != null) headers::tailbuffer::Nil
              else tailbuffer::Nil
    
    pipe.channelWrite(all)
  }

  private def writeLength(buffer: ByteBuffer, length: Int) {
    buffer.put(Integer.toHexString(length).getBytes(ASCII)).put(CRLFBytes)
  }

  private def encodeChunk(chunk: BodyChunk, last: List[ByteBuffer]): List[ByteBuffer] = {
    lengthBuffer.clear()
    writeLength(lengthBuffer, chunk.length)
    lengthBuffer.flip()

    val c = ByteBuffer.wrap(chunk.toArray)

    val list = lengthBuffer::c::CRLF::last

    if (headers != null) {
      val i = headers
      headers = null
      i::list
    } else list
  }
}

object ChunkProcessWriter {
  val ASCII = Charset.forName("US-ASCII")
  private val CRLFBytes = "\r\n".getBytes(ASCII)
  private val ChunkEndBytes = "0\r\n\r\n".getBytes(ASCII)
}
