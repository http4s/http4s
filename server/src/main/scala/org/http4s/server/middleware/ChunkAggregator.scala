package org.http4s
package server
package middleware

import cats.Functor
import cats.data.{NonEmptyList, OptionT}
import cats.effect.Effect
import cats.syntax.eq._
import cats.syntax.functor._
import cats.syntax.flatMap._
import fs2._
import fs2.interop.scodec.ByteVectorChunk
import org.http4s.EntityEncoder.chunkEncoder
import org.http4s.headers._
import scodec.bits.ByteVector

object ChunkAggregator {
  def apply[F[_]](service: HttpService[F])(implicit F: Effect[F]): HttpService[F] =
    service.flatMapF { response =>
      OptionT.liftF(response.body.compile.fold(ByteVector.empty.bufferBy(4096))(_ :+ _).flatMap {
        fullBody =>
          if (fullBody.nonEmpty)
            response
              .withBody(ByteVectorChunk(fullBody): Chunk[Byte])
              .map(removeChunkedTransferEncoding)
          else
            F.pure(response)
      })
    }

  private def removeChunkedTransferEncoding[F[_]: Functor]: Response[F] => Response[F] =
    _.transformHeaders { headers =>
      headers.flatMap {
        // Remove the `TransferCoding.chunked` value from the `Transfer-Encoding` header,
        // leaving the remaining values unchanged
        case e: `Transfer-Encoding` =>
          NonEmptyList
            .fromList(e.values.filterNot(_ === TransferCoding.chunked))
            .map(`Transfer-Encoding`.apply)
            .toList
        case header =>
          List(header)
      }
    }
}
