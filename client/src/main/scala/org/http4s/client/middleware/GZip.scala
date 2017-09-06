package org
package http4s
package client
package middleware

import cats.implicits._
import cats.effect.Async
import org.http4s.Compression.unzipResponse
import org.http4s.headers.`Content-Type`

object GZip {

  def apply[F[_]](client: Client[F], bufferSize: Int = 32 * 1024)(
      implicit F: Async[F]): Client[F] = {

    def openAndUnzip(req: Request[F]) =
      client.open(req).map {
        case dr @ DisposableResponse(resp, _) if isZipped(resp) =>
          val unZipped = unzipResponse(bufferSize, resp)
          dr.copy(response = unZipped)
        case dr => dr
      }

    client.copy(open = Service.lift(openAndUnzip))
  }

  private def isZipped[F[_]](resp: Response[F]): Boolean =
    resp.headers.get(`Content-Type`).exists(_.mediaType eq MediaType.`application/octet-stream`)
}
