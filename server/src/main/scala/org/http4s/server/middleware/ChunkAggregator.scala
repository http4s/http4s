package org.http4s
package server
package middleware

import cats.arrow.FunctionK
import cats.{FlatMap, Functor, ~>}
import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.Sync
import cats.implicits._
import fs2._
import org.http4s.headers._
import org.http4s.headers._

object ChunkAggregator {
  def apply[F[_]: FlatMap, G[_]: Sync, A](f: G ~> F)(
      @deprecatedName('service) http: Kleisli[F, A, Response[G]]): Kleisli[F, A, Response[G]] =
    http.flatMapF { response =>
      f(
        response.body.chunks.compile.toVector
          .map { vec =>
            val body = Chunk.concatBytes(vec)
            removeChunkedTransferEncoding[G](
              response.withBodyStream(Stream.chunk(body)),
              body.size.toLong)
          })
    }

  def httpRoutes[F[_]: Sync](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(OptionT.liftK[F])(httpRoutes)

  def httpApp[F[_]: Sync](httpApp: HttpApp[F]): HttpApp[F] =
    apply(FunctionK.id[F])(httpApp)

  private def removeChunkedTransferEncoding[G[_]: Functor](
      resp: Response[G],
      len: Long): Response[G] =
    resp.transformHeaders { headers =>
      val hs = headers.flatMap {
        // Remove the `TransferCoding.chunked` value from the `Transfer-Encoding` header,
        // leaving the remaining values unchanged
        case e: `Transfer-Encoding` =>
          NonEmptyList
            .fromList(e.values.filterNot(_ === TransferCoding.chunked))
            .map(`Transfer-Encoding`.apply)
            .toList
        case `Content-Length`(_) =>
          Nil
        case header =>
          List(header)
      }
      if (len > 0L) hs.put(`Content-Length`.unsafeFromLong(len))
      else hs
    }
}
