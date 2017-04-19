package org.http4s
package server
package middleware

import fs2._
import fs2.interop.cats._
import org.http4s.headers._

object ChunkAggregator {
  def apply(service: HttpService): HttpService = service.flatMapF[MaybeResponse] {
    case response: Response =>
      response.body.runLog.map { bytes =>
        if (bytes.nonEmpty) {
          val h = response.headers.put(`Content-Length`(bytes.length.toLong))
          response.copy(body = Stream.emits(bytes), headers = h)
        } else response
      }
    case Pass => Task.now(Pass)
  }
}
