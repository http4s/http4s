package org.http4s
package blaze

import java.nio.ByteBuffer
import pipeline.TailStage
import scala.concurrent.{Promise, Future, ExecutionContext}
import scalaz.concurrent.Task
import java.nio.charset.StandardCharsets
import org.http4s.util.StringWriter
import scodec.bits.ByteVector
import scalaz.{\/-, -\/}

/**
 * @author Bryce Anderson
 *         Created on 1/10/14
 */
class ChunkProcessWriter(private var headers: ByteBuffer, pipe: TailStage[ByteBuffer], trailer: Task[Headers])
                              (implicit val ec: ExecutionContext) extends ProcessWriter {

  import ChunkProcessWriter._

  private def CRLF = ByteBuffer.wrap(CRLFBytes).asReadOnlyBuffer()

  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Any] = {
    pipe.channelWrite(encodeChunk(chunk, Nil))
  }

  protected def writeEnd(chunk: ByteVector): Future[Any] = {
    def writeTrailer = {
      val promise = Promise[Any]
      trailer.map { trailerHeaders =>
        if (trailerHeaders.nonEmpty) {
          val rr = new StringWriter(256)
          rr ~ '0' ~ '\r' ~ '\n'             // Last chunk
          trailerHeaders.foreach( h =>  rr ~ h.name.toString ~ ": " ~ h ~ '\r' ~ '\n')   // trailers
          rr ~ '\r' ~ '\n'          // end of chunks
          ByteBuffer.wrap(rr.result().getBytes(StandardCharsets.US_ASCII))
        } else ByteBuffer.wrap(ChunkEndBytes)
      }.runAsync {
        case \/-(buffer) => promise.completeWith(pipe.channelWrite(buffer))
        case -\/(t) => promise.failure(t)
      }
      promise.future
    }
    if (chunk.nonEmpty) writeBodyChunk(chunk, true).flatMap { _ => writeTrailer }
    else writeTrailer
  }

  private def writeLength(length: Int): ByteBuffer = {
    val bytes = Integer.toHexString(length).getBytes(StandardCharsets.US_ASCII)
    val b = ByteBuffer.allocate(bytes.length + 2)
    b.put(bytes).put(CRLFBytes).flip()
    b
  }

  private def encodeChunk(chunk: ByteVector, last: List[ByteBuffer]): List[ByteBuffer] = {
    val list = writeLength(chunk.length)::chunk.toByteBuffer::CRLF::last
    if (headers != null) {
      val i = headers
      headers = null
      i::list
    } else list
  }
}

object ChunkProcessWriter {
  private val CRLFBytes = "\r\n".getBytes(StandardCharsets.US_ASCII)
  private val ChunkEndBytes = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
}
