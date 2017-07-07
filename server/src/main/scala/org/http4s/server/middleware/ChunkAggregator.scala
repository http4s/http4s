package org.http4s
package server
package middleware

import cats.Functor
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import fs2._
import fs2.interop.scodec.ByteVectorChunk
import org.http4s.EntityEncoder.chunkEncoder
import org.http4s.headers._
import scodec.bits.ByteVector

object ChunkAggregator {
  def apply[F[_]](service: HttpService[F])(implicit F: Sync[F]): HttpService[F] =
    service.flatMapF[MaybeResponse[F]] {
      case Pass() => Pass.pure
      case response: Response[F] =>
        response.body.runFold(ByteVector.empty.bufferBy(4096))(_ :+ _).flatMap { fullBody =>
          if (fullBody.nonEmpty)
            response.withBody(ByteVectorChunk(fullBody): Chunk[Byte]).map(removeChunkedTransferEncoding)
          else
            F.pure(response)
        }
    }

  private def removeChunkedTransferEncoding[F[_]: Functor]: Response[F] => Response[F] = {
    _.transformHeaders { headers =>
      headers.flatMap {
        // Remove the `TransferCoding.chunked` value from the `Transfer-Encoding` header,
        // leaving the remaining values unchanged
        case e: `Transfer-Encoding` =>
          NonEmptyList.fromList(e.values.filterNot(_ == TransferCoding.chunked)).map(`Transfer-Encoding`.apply).toList
        case header =>
          List(header)
      }
    }
  }
}
