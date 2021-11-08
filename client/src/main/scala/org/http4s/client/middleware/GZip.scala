/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package client
package middleware

import cats.data.NonEmptyList
import cats.effect.BracketThrow
import cats.effect.Sync
import fs2.Pipe
import fs2.Pull
import fs2.Stream
import org.http4s.headers.`Accept-Encoding`
import org.http4s.headers.`Content-Encoding`
import org.typelevel.ci._

import scala.util.control.NoStackTrace

/** Client middleware for enabling gzip.
  */
object GZip {
  private val supportedCompressions =
    NonEmptyList.of(ContentCoding.gzip, ContentCoding.deflate)

  def apply[F[_]](bufferSize: Int = 32 * 1024)(client: Client[F])(implicit F: Sync[F]): Client[F] =
    Client[F] { req =>
      val reqWithEncoding = addHeaders(req)
      val responseResource = client.run(reqWithEncoding)

      responseResource.map { actualResponse =>
        decompress(bufferSize, actualResponse)
      }
    }

  private def addHeaders[F[_]](req: Request[F]): Request[F] =
    req.headers.get[`Accept-Encoding`] match {
      case Some(_) =>
        req
      case _ =>
        req.putHeaders(`Accept-Encoding`(supportedCompressions))
    }

  private def decompress[F[_]](bufferSize: Int, response: Response[F])(implicit
      F: Sync[F]
  ): Response[F] =
    response.headers.get[`Content-Encoding`] match {
      case Some(header)
          if header.contentCoding == ContentCoding.gzip || header.contentCoding == ContentCoding.`x-gzip` =>
        val gunzip: Pipe[F, Byte, Byte] =
          _.through(fs2.compression.gunzip(bufferSize)).flatMap(_.content)
        response
          .filterHeaders(nonCompressionHeader)
          .withBodyStream(response.body.through(decompressWith(gunzip)))

      case Some(header) if header.contentCoding == ContentCoding.deflate =>
        val deflate: Pipe[F, Byte, Byte] = fs2.compression.deflate(bufferSize)
        response
          .filterHeaders(nonCompressionHeader)
          .withBodyStream(response.body.through(decompressWith(deflate)))

      case _ =>
        response
    }

  private def decompressWith[F[_]](
      decompressor: Pipe[F, Byte, Byte]
  )(implicit F: BracketThrow[F]): Pipe[F, Byte, Byte] =
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

  private def nonCompressionHeader(header: Header.Raw): Boolean =
    header.name != ci"Content-Encoding" &&
      header.name != ci"Content-Length"

  private object EmptyBodyException extends Throwable with NoStackTrace
}
