package org.http4s
package server
package middleware

import cats.arrow.FunctionK
import cats.{FlatMap, ~>}
import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.Sync
import cats.implicits._
import fs2._
import org.http4s.headers._
import scala.collection.mutable.ListBuffer

object ChunkAggregator {
  def apply[F[_]: FlatMap, G[_]: Sync, A](f: G ~> F)(
      http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    http.flatMapF { response =>
      f(
        response.body.chunks.compile.toVector
          .map { vec =>
            val body = Chunk.concatBytes(vec)
            response
              .withBodyStream(Stream.chunk(body))
              .transformHeaders(removeChunkedTransferEncoding(body.size.toLong))
          })
    }

  def httpRoutes[F[_]: Sync](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(OptionT.liftK[F])(httpRoutes)

  def httpApp[F[_]: Sync](httpApp: HttpApp[F]): HttpApp[F] =
    apply(FunctionK.id[F])(httpApp)

  /* removes the `TransferCoding.chunked` value from the `Transfer-Encoding` header,
   removes the `Content-Length` header, and leaves the other headers unchanged */
  private[this] def removeChunkedTransferEncoding(len: Long)(headers: Headers): Headers = {
    val hh: ListBuffer[Header] = ListBuffer.empty[Header]
    headers.toList.foreach {
      case e: `Transfer-Encoding` =>
        e.values.filterNot(_ === TransferCoding.chunked) match {
          case v :: vs =>
            hh += `Transfer-Encoding`(NonEmptyList(v, vs))
          case Nil => // do nothing
        }
      case `Content-Length`(_) => // do nothing
      case header => hh += header
    }
    if (len > 0L)
      hh += `Content-Length`.unsafeFromLong(len)
    Headers(hh.toList)
  }
}
