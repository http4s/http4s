package org.http4s.blaze.util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1

import org.http4s.Headers
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter

import scodec.bits.ByteVector

import scala.concurrent.{ExecutionContext, Future, Promise}
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

class ChunkProcessWriter(private var headers: StringWriter,
                         pipe: TailStage[ByteBuffer],
                         trailer: Task[Headers])
                         (implicit val ec: ExecutionContext) extends ProcessWriter {

  import org.http4s.blaze.util.ChunkProcessWriter._

  protected def writeBodyChunk(chunk: ByteVector, flush: Boolean): Future[Unit] = {
    if (chunk.nonEmpty) pipe.channelWrite(encodeChunk(chunk, Nil))
    else Future.successful(())
  }

  protected def writeEnd(chunk: ByteVector): Future[Boolean] = {
    def writeTrailer = {
      val promise = Promise[Boolean]
      trailer.map { trailerHeaders =>
        if (trailerHeaders.nonEmpty) {
          val rr = new StringWriter(256)
          rr << "0\r\n" // Last chunk
          trailerHeaders.foreach( h =>  rr << h.name.toString << ": " << h << "\r\n") // trailers
          rr << "\r\n" // end of chunks
          ByteBuffer.wrap(rr.result().getBytes(ISO_8859_1))
        }
        else ChunkEndBuffer
      }.runAsync {
        case \/-(buffer) => promise.completeWith(pipe.channelWrite(buffer).map(Function.const(false)))
        case -\/(t) => promise.failure(t)
      }
      promise.future
    }

    val f = if (headers != null) {  // This is the first write, so we can add a body length instead of chunking
      val h = headers
      headers = null

      if (chunk.nonEmpty) {
        val body = chunk.toByteBuffer
        h << s"Content-Length: ${body.remaining()}\r\n\r\n"
        
        // Trailers are optional, so dropping because we have no body.
        val hbuff = ByteBuffer.wrap(h.result().getBytes(ISO_8859_1))
        pipe.channelWrite(hbuff::body::Nil)
      }
      else {
        h << s"Content-Length: 0\r\n\r\n"
        val hbuff = ByteBuffer.wrap(h.result().getBytes(ISO_8859_1))
        pipe.channelWrite(hbuff)
      }
    } else {
      if (chunk.nonEmpty) writeBodyChunk(chunk, true).flatMap { _ => writeTrailer }
      else writeTrailer
    }

    f.map(Function.const(false))
  }

  private def writeLength(length: Int): ByteBuffer = {
    val bytes = Integer.toHexString(length).getBytes(ISO_8859_1)
    val b = ByteBuffer.allocate(bytes.length + 2)
    b.put(bytes).put(CRLFBytes).flip()
    b
  }

  private def encodeChunk(chunk: ByteVector, last: List[ByteBuffer]): List[ByteBuffer] = {
    val list = writeLength(chunk.length)::chunk.toByteBuffer::CRLF::last
    if (headers != null) {
      val i = headers
      i << "Transfer-Encoding: chunked\r\n\r\n"
      val b = ByteBuffer.wrap(i.result().getBytes(ISO_8859_1))
      headers = null
      b::list
    } else list
  }
}

object ChunkProcessWriter {
  private val CRLFBytes = "\r\n".getBytes(ISO_8859_1)

  private def CRLF = CRLFBuffer.duplicate()
  private def ChunkEndBuffer = chunkEndBuffer.duplicate()

  private[this] val CRLFBuffer = ByteBuffer.wrap(CRLFBytes).asReadOnlyBuffer()
  private[this] val chunkEndBuffer =
    ByteBuffer.wrap("0\r\n\r\n".getBytes(ISO_8859_1)).asReadOnlyBuffer()
}
