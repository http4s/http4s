package org.http4s
package client
package middleware

import cats.effect.Sync
import org.http4s.headers.{`Accept-Encoding`, `Content-Encoding`}

/**
  * Client middleware for enabling gzip.
  */
object GZip {

  private val supportedCompressions =
    Seq(ContentCoding.gzip.coding, ContentCoding.deflate.coding).mkString(", ")

  def apply[F[_]: Sync](bufferSize: Int = 32 * 1024)(client: Client[F]): Client[F] =
    Client[F] { req =>
      val reqWithEncoding = addHeaders(req)
      val responseResource = client.run(reqWithEncoding)

      responseResource.map { actualResponse =>
        actualResponse.withBodyStream(decompress(bufferSize, actualResponse))
      }
    }

  private def addHeaders[F[_]: Sync](req: Request[F]): Request[F] =
    req.headers.get(`Accept-Encoding`) match {
      case Some(_) =>
        req
      case _ =>
        req.withHeaders(
          req.headers ++ Headers.of(Header(`Accept-Encoding`.name.value, supportedCompressions)))
    }

  private def decompress[F[_]: Sync](bufferSize: Int, response: Response[F]): EntityBody[F] =
    response.headers.get(`Content-Encoding`) match {
      case Some(header)
          if header.contentCoding == ContentCoding.gzip || header.contentCoding == ContentCoding.`x-gzip` =>
        response.body.through(fs2.compress.gunzip(bufferSize))

      case Some(header) if header.contentCoding == ContentCoding.deflate =>
        response.body.through(fs2.compress.deflate(bufferSize = bufferSize))

      case _ =>
        response.body
    }
}
