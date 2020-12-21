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

import cats.effect.Async
import fs2.{Pipe, Pull, Stream}
import org.http4s.headers.{`Accept-Encoding`, `Content-Encoding`}
import scala.util.control.NoStackTrace
import fs2.compression.DeflateParams

/** Client middleware for enabling gzip.
  */
object GZip {
  private val supportedCompressions =
    Seq(ContentCoding.gzip.coding, ContentCoding.deflate.coding).mkString(", ")

  def apply[F[_]](bufferSize: Int = 32 * 1024)(client: Client[F])(implicit F: Async[F]): Client[F] =
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
          req.headers ++ Headers.of(Header(`Accept-Encoding`.name.toString, supportedCompressions)))
    }

  private def decompress[F[_]](bufferSize: Int, response: Response[F])(implicit
      F: Async[F]): Response[F] =
    response.headers.get(`Content-Encoding`) match {
      case Some(header)
          if header.contentCoding == ContentCoding.gzip || header.contentCoding == ContentCoding.`x-gzip` =>
        val gunzip: Pipe[F, Byte, Byte] =
          _.through(fs2.compression.gunzip(bufferSize)).flatMap(_.content)
        response.withBodyStream(response.body.through(decompressWith(gunzip)))

      case Some(header) if header.contentCoding == ContentCoding.deflate =>
        val deflate: Pipe[F, Byte, Byte] = fs2.compression.deflate(DeflateParams(bufferSize))
        response.withBodyStream(response.body.through(decompressWith(deflate)))

      case _ =>
        response
    }

  private def decompressWith[F[_]](decompressor: Pipe[F, Byte, Byte])(implicit
      F: Async[F]): Pipe[F, Byte, Byte] =
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
