package org.http4s
package client
package middleware

import cats.effect.Bracket
import com.github.ghik.silencer.silent
import org.http4s.headers.{`Accept-Encoding`, `Content-Encoding`, `Content-Length`}
import org.http4s.implicits._

/**
  * Client middleware for enabling gzip.
  */
object GZip {
  private val supportedCompressions =
    Seq(ContentCoding.gzip.coding, ContentCoding.deflate.coding).mkString(", ")

  def apply[F[_]](bufferSize: Int = 32 * 1024)(client: Client[F])(
      implicit F: Bracket[F, Throwable]): Client[F] =
    Client[F] { req =>
      val reqWithEncoding = addHeaders(req)
      val responseResource = client.run(reqWithEncoding)

      responseResource.map { actualResponse =>
        decompress(bufferSize, actualResponse)
      }
    }

  private def addHeaders[F[_]](req: Request[F]): Request[F] =
    req.headers.get(`Accept-Encoding`) match {
      case Some(_) =>
        req
      case _ =>
        req.withHeaders(
          req.headers ++ Headers.of(Header(`Accept-Encoding`.name.value, supportedCompressions)))
    }

  @silent("deprecated")
  private def decompress[F[_]](bufferSize: Int, response: Response[F])(
      implicit F: Bracket[F, Throwable]): Response[F] =
    response.headers.get(`Content-Encoding`) match {
      case Some(header)
          if header.contentCoding == ContentCoding.gzip || header.contentCoding == ContentCoding.`x-gzip` =>
        response
          .filterHeaders(nonCompressionHeader)
          .withBodyStream(response.body.through(fs2.compress.gunzip(bufferSize)))

      case Some(header) if header.contentCoding == ContentCoding.deflate =>
        response
          .filterHeaders(nonCompressionHeader)
          .withBodyStream(response.body.through(fs2.compress.deflate(bufferSize = bufferSize)))

      case _ =>
        response
    }

  private def nonCompressionHeader(header: Header): Boolean =
    header.isNot(`Content-Encoding`) && header.isNot(`Content-Length`)
}
