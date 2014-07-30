package org.http4s.blaze.util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.http4s.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future, Promise}
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

class ChunkProcessWriter(private var headers: StringWriter, pipe: TailStage[ByteBuffer], trailer: Task[Headers])
                              (implicit val ec: ExecutionContext) extends ProcessWriter {

  import org.http4s.blaze.util.ChunkProcessWriter._

  private def CRLF = ByteBuffer.wrap(CRLFBytes).asReadOnlyBuffer()

  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = {
    pipe.channelWrite(encodeChunk(chunk, Nil))
  }

  protected def writeEnd(chunk: ByteVector): Future[Unit] = {
    def writeTrailer = {
      val promise = Promise[Unit]
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

    if (headers != null) {  // This is the first write, so we can add a body length instead of chunking
      val h = headers
      headers = null

      if (chunk.nonEmpty) {
        val body = chunk.toByteBuffer
        h ~ s"Content-Length: ${body.remaining()}\r\n\r\n"
        
        // Trailers are optional, so dropping because we have no body.
        val hbuff = ByteBuffer.wrap(h.result().getBytes(StandardCharsets.US_ASCII))
        pipe.channelWrite(hbuff::body::Nil)
      }
      else {
        h ~ s"Content-Length: 0\r\n\r\n"
        val hbuff = ByteBuffer.wrap(h.result().getBytes(StandardCharsets.US_ASCII))
        pipe.channelWrite(hbuff)
      }
    } else {
      if (chunk.nonEmpty) writeBodyChunk(chunk, true).flatMap { _ => writeTrailer }
      else writeTrailer
    }
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
      i ~ "Transfer-Encoding: chunked\r\n\r\n"
      val b = ByteBuffer.wrap(i.result().getBytes(StandardCharsets.US_ASCII))
      headers = null
      b::list
    } else list
  }
}

object ChunkProcessWriter {
  private val CRLFBytes = "\r\n".getBytes(StandardCharsets.US_ASCII)
  private val ChunkEndBytes = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
}
