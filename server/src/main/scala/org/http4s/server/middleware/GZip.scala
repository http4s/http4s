package org.http4s
package server
package middleware

import java.util.zip.{CRC32, Deflater}
import javax.xml.bind.DatatypeConverter

import cats._
import fs2.Stream._
import fs2._
import fs2.compress._
import org.http4s.headers._
import org.log4s.getLogger

object GZip {
  private[this] val logger = getLogger

  // TODO: It could be possible to look for Task.now type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply[F[_]: Functor](
      service: HttpService[F],
      bufferSize: Int = 32 * 1024,
      level: Int = Deflater.DEFAULT_COMPRESSION): HttpService[F] =
    Service.lift { req: Request[F] =>
      req.headers.get(`Accept-Encoding`) match {
        case Some(acceptEncoding) if satisfiedByGzip(acceptEncoding) =>
          service.map(zipOrPass(_, bufferSize, level)).apply(req)
        case _ => service(req)
      }
    }

  private def satisfiedByGzip(acceptEncoding: `Accept-Encoding`) =
    acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
      ContentCoding.`x-gzip`)

  private def zipOrPass[F[_]: Functor](
      response: MaybeResponse[F],
      bufferSize: Int,
      level: Int): MaybeResponse[F] =
    response match {
      case resp: Response[F] if isZippable(resp) => Compression.zipResponse(bufferSize, level, resp)
      case resp: Response[F] => resp // Don't touch it, Content-Encoding already set
      case Pass() => Pass()
    }

  private def isZippable[F[_]](resp: Response[F]): Boolean = {
    val contentType = resp.headers.get(`Content-Type`)
    resp.headers.get(`Content-Encoding`).isEmpty &&
    (contentType.isEmpty || contentType.get.mediaType.compressible ||
    (contentType.get.mediaType eq MediaType.`application/octet-stream`))
  }
}
