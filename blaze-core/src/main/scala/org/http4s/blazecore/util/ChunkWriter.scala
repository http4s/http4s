package org.http4s
package blazecore
package util

import cats.effect.{Effect, IO}
import cats.implicits._
import fs2._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1
import org.http4s.blaze.pipeline.TailStage
import org.http4s.internal.unsafeRunAsync
import org.http4s.util.StringWriter
import scala.concurrent._

private[util] object ChunkWriter {
  val CRLFBytes = "\r\n".getBytes(ISO_8859_1)
  private[this] val CRLFBuffer = ByteBuffer.wrap(CRLFBytes).asReadOnlyBuffer()
  def CRLF = CRLFBuffer.duplicate()

  private[this] val chunkEndBuffer =
    ByteBuffer.wrap("0\r\n\r\n".getBytes(ISO_8859_1)).asReadOnlyBuffer()
  def ChunkEndBuffer = chunkEndBuffer.duplicate()

  val TransferEncodingChunkedString = "Transfer-Encoding: chunked\r\n\r\n"
  private[this] val TransferEncodingChunkedBytes =
    "Transfer-Encoding: chunked\r\n\r\n".getBytes(ISO_8859_1)
  private[this] val transferEncodingChunkedBuffer =
    ByteBuffer.wrap(TransferEncodingChunkedBytes).asReadOnlyBuffer
  def TransferEncodingChunked = transferEncodingChunkedBuffer.duplicate()

  def writeTrailer[F[_]](pipe: TailStage[ByteBuffer], trailer: F[Headers])(
      implicit F: Effect[F],
      ec: ExecutionContext): Future[Boolean] = {
    val promise = Promise[Boolean]
    val f = trailer.map { trailerHeaders =>
      if (trailerHeaders.nonEmpty) {
        val rr = new StringWriter(256)
        rr << "0\r\n" // Last chunk
        trailerHeaders.foreach(h => h.render(rr) << "\r\n") // trailers
        rr << "\r\n" // end of chunks
        ByteBuffer.wrap(rr.result.getBytes(ISO_8859_1))
      } else ChunkEndBuffer
    }
    unsafeRunAsync(f) {
      case Right(buffer) =>
        IO { promise.completeWith(pipe.channelWrite(buffer).map(Function.const(false))); () }
      case Left(t) =>
        IO { promise.failure(t); () }
    }
    promise.future
  }

  def writeLength(length: Long): ByteBuffer = {
    val bytes = length.toHexString.getBytes(ISO_8859_1)
    val b = ByteBuffer.allocate(bytes.length + 2)
    b.put(bytes).put(CRLFBytes).flip()
    b
  }

  def encodeChunk(chunk: Chunk[Byte], last: List[ByteBuffer]): List[ByteBuffer] =
    writeLength(chunk.size.toLong) :: chunk.toByteBuffer :: CRLF :: last
}
