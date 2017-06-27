package org.http4s
package server
package middleware

import cats.data.NonEmptyList
import fs2._
import fs2.interop.cats._
import org.http4s.headers._

object ChunkAggregator {
  def apply(service: HttpService): HttpService =
    service.flatMapF[MaybeResponse] {
      case Pass => Task.now(Pass)
      case response: Response =>
        response.body.runLog.flatMap { fullBody =>
          if (fullBody.nonEmpty)
            response.withBody(Chunk.seq(fullBody)).map(removeChunkedTransferEncoding)
          else
            Task.now(response)
        }
    }

  private val removeChunkedTransferEncoding: Response => Response = {
    _.transformHeaders { headers =>
      headers.flatMap {
        case e: `Transfer-Encoding` =>
          NonEmptyList.fromList(e.values.filterNot(_ == TransferCoding.chunked)).map(`Transfer-Encoding`.apply).toList
        case header =>
          List(header)
      }
    }
  }
}
