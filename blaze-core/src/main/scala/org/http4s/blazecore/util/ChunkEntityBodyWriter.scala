package org.http4s
package blazecore
package util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.StringWriter
import org.http4s.util.chunk._

import scala.concurrent._

class ChunkEntityBodyWriter[F[_]](private var headers: StringWriter,
                                  pipe: TailStage[ByteBuffer],
                                  trailer: F[Headers])
                                 (implicit protected val F: Effect[F],
                                  protected val ec: ExecutionContext)
  extends EntityBodyWriter[F] {

  import ChunkEntityBodyWriter._

  protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    if (chunk.isEmpty) Future.successful(())
    else pipe.channelWrite(encodeChunk(chunk, Nil))

  protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    def writeTrailer = {
      val promise = Promise[Boolean]
      val f = trailer.map { trailerHeaders =>
        if (trailerHeaders.nonEmpty) {
          val rr = new StringWriter(256)
          rr << "0\r\n" // Last chunk
          trailerHeaders.foreach( h =>  rr << h.name.toString << ": " << h << "\r\n") // trailers
          rr << "\r\n" // end of chunks
          ByteBuffer.wrap(rr.result.getBytes(ISO_8859_1))
        }
        else ChunkEndBuffer
      }
      async.unsafeRunAsync(f) {
        case Right(buffer) =>
          IO(promise.completeWith(pipe.channelWrite(buffer).map(Function.const(false))))
        case Left(t) =>
          IO(promise.failure(t))
      }
      promise.future
    }

    val f = if (headers != null) {  // This is the first write, so we can add a body length instead of chunking
      val h = headers
      headers = null

      if (!chunk.isEmpty) {
        val body = chunk.toByteBuffer
        h << s"Content-Length: ${body.remaining()}\r\n\r\n"
        
        // Trailers are optional, so dropping because we have no body.
        val hbuff = ByteBuffer.wrap(h.result.getBytes(ISO_8859_1))
        pipe.channelWrite(hbuff::body::Nil)
      }
      else {
        h << s"Content-Length: 0\r\n\r\n"
        val hbuff = ByteBuffer.wrap(h.result.getBytes(ISO_8859_1))
        pipe.channelWrite(hbuff)
      }
    } else {
      if (!chunk.isEmpty) writeBodyChunk(chunk, flush = true).flatMap(_ => writeTrailer)
      else writeTrailer
    }

    f.map(Function.const(false))
  }

  private def writeLength(length: Long): ByteBuffer = {
    val bytes = length.toHexString.getBytes(ISO_8859_1)
    val b = ByteBuffer.allocate(bytes.length + 2)
    b.put(bytes).put(CRLFBytes).flip()
    b
  }

  private def encodeChunk(chunk: Chunk[Byte], last: List[ByteBuffer]): List[ByteBuffer] = {
    val list = writeLength(chunk.size.toLong) :: chunk.toByteBuffer :: CRLF :: last
    if (headers != null) {
      headers << "Transfer-Encoding: chunked\r\n\r\n"
      val b = ByteBuffer.wrap(headers.result.getBytes(ISO_8859_1))
      headers = null
      b::list
    } else list
  }
}

object ChunkEntityBodyWriter {
  private val CRLFBytes = "\r\n".getBytes(ISO_8859_1)

  private def CRLF = CRLFBuffer.duplicate()
  private def ChunkEndBuffer = chunkEndBuffer.duplicate()

  private[this] val CRLFBuffer = ByteBuffer.wrap(CRLFBytes).asReadOnlyBuffer()
  private[this] val chunkEndBuffer =
    ByteBuffer.wrap("0\r\n\r\n".getBytes(ISO_8859_1)).asReadOnlyBuffer()
}
