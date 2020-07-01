/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect.Bracket
import com.github.ghik.silencer.silent
import fs2.{Pipe, Pull, Stream}
import org.http4s.headers.{`Accept-Encoding`, `Content-Encoding`}
import scala.util.control.NoStackTrace

/**
  * Client middleware for enabling gzip.
  */
object GZip {
  private val supportedCompressions =
    Seq(ContentCoding.gzip.coding, ContentCoding.deflate.coding).mkString(", ")

  def apply[F[_]](bufferSize: Int = 32 * 1024)(client: Client[F])(implicit
      F: Bracket[F, Throwable]): Client[F] =
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
  private def decompress[F[_]](bufferSize: Int, response: Response[F])(implicit
      F: Bracket[F, Throwable]): Response[F] =
    response.headers.get(`Content-Encoding`) match {
      case Some(header)
          if header.contentCoding == ContentCoding.gzip || header.contentCoding == ContentCoding.`x-gzip` =>
        val gunzip: Pipe[F, Byte, Byte] =
          _.through(fs2.compress.gunzip(bufferSize))
        response.withBodyStream(response.body.through(decompressWith(gunzip)))

      case Some(header) if header.contentCoding == ContentCoding.deflate =>
        val deflate: Pipe[F, Byte, Byte] = fs2.compress.deflate(bufferSize)
        response.withBodyStream(response.body.through(decompressWith(deflate)))

      case _ =>
        response
    }

  private def decompressWith[F[_]](decompressor: Pipe[F, Byte, Byte])(implicit
      F: Bracket[F, Throwable]): Pipe[F, Byte, Byte] =
    _.pull.peek1
      .flatMap {
        case None => Pull.raiseError(EmptyBodyException)
        case Some((_, fullStream)) => Pull.output1(fullStream)
      }
      .stream
      .flatten
      .through(decompressor)
      .handleErrorWith {
        case EmptyBodyException => Stream.empty
        case error => Stream.raiseError(error)
      }

  private object EmptyBodyException extends Throwable with NoStackTrace
}
