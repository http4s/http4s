package org.http4s
package server
package middleware

import cats.data.NonEmptyList
import fs2._
import fs2.interop.cats._
import org.http4s.EntityEncoder.chunkEncoder
import org.http4s.headers._
import org.http4s.util.ByteVectorChunk
import scodec.bits.ByteVector

object ChunkAggregator {
  def apply(service: HttpService): HttpService =
    service.flatMapF[MaybeResponse] {
      case Pass => Task.now(Pass)
      case response: Response =>
        response.body.runFold(ByteVector.empty.bufferBy(4096))(_ :+ _).flatMap { fullBody =>
          if (fullBody.nonEmpty)
            response.withBody(ByteVectorChunk(fullBody): Chunk[Byte]).map(removeChunkedTransferEncoding)
          else
            Task.now(response)
        }
    }

  private val removeChunkedTransferEncoding: Response => Response = {
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
