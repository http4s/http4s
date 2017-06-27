package org.http4s.blaze.util

import cats.effect._
import fs2._
import org.http4s.blaze.http.Headers
import org.http4s.blaze.http.http20.NodeMsg._
import org.http4s.blaze.pipeline.TailStage
import org.http4s.util.chunk._

import scala.concurrent._

class Http2Writer[F[_]](tail: TailStage[Http2Msg],
                        private var headers: Headers,
                        protected val ec: ExecutionContext)
                       (implicit protected val F: Effect[F])
  extends EntityBodyWriter[F] {

  override protected def writeEnd(chunk: Chunk[Byte]): Future[Boolean] = {
    val f = if (headers == null) tail.channelWrite(DataFrame(endStream = true, chunk.toByteBuffer))
    else {
      val hs = headers
      headers = null
      if (chunk.isEmpty) tail.channelWrite(HeadersFrame(None, endStream = true, hs))
      else tail.channelWrite(HeadersFrame(None, endStream = false, hs)
        :: DataFrame(endStream = true, chunk.toByteBuffer)
        :: Nil)
    }

    f.map(Function.const(false))(ec)
  }

  override protected def writeBodyChunk(chunk: Chunk[Byte], flush: Boolean): Future[Unit] = {
    if (chunk.isEmpty) Future.successful(())
    else {
      if (headers == null) tail.channelWrite(DataFrame(endStream = false, chunk.toByteBuffer))
      else {
        val hs = headers
        headers = null
        tail.channelWrite(HeadersFrame(None, endStream = false, hs)
          :: DataFrame(endStream = false, chunk.toByteBuffer)
          :: Nil)
      }
    }
  }
}
