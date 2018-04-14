package org.http4s
package server
package middleware

import cats.Functor
import cats.data.{NonEmptyList, OptionT}
import cats.effect.Effect
import cats.syntax.eq._
import cats.syntax.functor._
import fs2._
import org.http4s.headers._

object ChunkAggregator {
  def apply[F[_]](@deprecatedName('service, "0.19") routes: HttpRoutes[F])(
      implicit F: Effect[F]): HttpRoutes[F] =
    routes.flatMapF { response =>
      OptionT.liftF(
        response.body.chunks.compile
          .fold((Segment.empty[Byte], 0L)) {
            case ((seg, len), c) => (seg ++ c.toSegment, len + c.size)
          }
          .map {
            case (body, len) =>
              removeChunkedTransferEncoding[F](response.withBodyStream(Stream.segment(body)), len)
          })
    }

  private def removeChunkedTransferEncoding[F[_]: Functor](
      resp: Response[F],
      len: Long): Response[F] =
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
