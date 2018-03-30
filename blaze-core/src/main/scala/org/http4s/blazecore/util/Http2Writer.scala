package org.http4s
package blazecore
package util

import cats.effect._
import fs2._
import org.http4s.blaze.http.Headers
import org.http4s.blaze.http.http2.{DataFrame, HeadersFrame, Priority, StreamFrame}
import org.http4s.blaze.pipeline.TailStage
import scala.concurrent._

private[http4s] class Http2Writer[F[_]](
    tail: TailStage[StreamFrame],
    private var headers: Headers,
    protected val ec: ExecutionContext)(implicit protected val F: Effect[F])
    extends EntityBodyWriter[F] {

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    val f =
      if (headers == null) tail.channelWrite(DataFrame(endStream = true, chunk.toByteBuffer))
      else {
        val hs = headers
        headers = null
        if (chunk.isEmpty)
          tail.channelWrite(HeadersFrame(Priority.NoPriority, endStream = true, hs))
        else
          tail.channelWrite(
            HeadersFrame(Priority.NoPriority, endStream = false, hs)
              :: DataFrame(endStream = true, chunk.toByteBuffer)
              :: Nil)
      }

    f.map(Function.const(false))(ec)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] =
    if (chunk.isEmpty) FutureUnit
    else {
      if (headers == null) tail.channelWrite(DataFrame(endStream = false, chunk.toByteBuffer))
      else {
        val hs = headers
        headers = null
        tail.channelWrite(
          HeadersFrame(Priority.NoPriority, endStream = false, hs)
            :: DataFrame(endStream = false, chunk.toByteBuffer)
            :: Nil)
      }
    }
}
