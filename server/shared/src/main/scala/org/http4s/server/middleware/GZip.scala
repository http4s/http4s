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
package server
package middleware

import cats.Applicative
import cats.Functor
import cats.Monad
import cats.data.Kleisli
import cats.syntax.all._
import fs2.compression._
import org.http4s.headers._
import org.typelevel.log4cats

object GZip {

  // TODO: It could be possible to look for F.pure type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply[F[_]: Monad: log4cats.LoggerFactory, G[_]: Compression](
      http: Http[F, G],
      bufferSize: Int = 32 * 1024,
      level: DeflateParams.Level = DeflateParams.Level.DEFAULT,
      isZippable: Response[G] => Boolean = defaultIsZippable[G](_: Response[G]),
  ): Http[F, G] = {
    implicit val logger: log4cats.Logger[F] = log4cats.LoggerFactory[F].getLogger
    Kleisli { (req: Request[G]) =>
      req.headers.get[`Accept-Encoding`] match {
        case Some(acceptEncoding) if satisfiedByGzip(acceptEncoding) =>
          http(req).flatMap(zipOrPass[F, G](_, bufferSize, level, isZippable))
        case _ => http(req)
      }
    }
  }

  def defaultIsZippable[F[_]](resp: Response[F]): Boolean = {
    val contentType = resp.headers.get[`Content-Type`]
    !resp.headers.contains[`Content-Encoding`] &&
    resp.status.isEntityAllowed &&
    (contentType.isEmpty || contentType.get.mediaType.compressible ||
      (contentType.get.mediaType == MediaType.application.`octet-stream`))
  }

  private def satisfiedByGzip(acceptEncoding: `Accept-Encoding`) =
    acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
      ContentCoding.`x-gzip`
    )

  private def zipOrPass[F[_]: Applicative: log4cats.Logger, G[_]: Compression](
      response: Response[G],
      bufferSize: Int,
      level: DeflateParams.Level,
      isZippable: Response[G] => Boolean,
  ): F[Response[G]] =
    response match {
      case resp if isZippable(resp) => zipResponse(bufferSize, level, resp)
      case resp => resp.pure // Don't touch it, Content-Encoding already set
    }

  private def zipResponse[F[_]: Functor: log4cats.Logger, G[_]: Compression](
      bufferSize: Int,
      level: DeflateParams.Level,
      resp: Response[G],
  ): F[Response[G]] = {
    val compressPipe =
      Compression[G].gzip(
        fileName = None,
        modificationTime = None,
        comment = None,
        DeflateParams(
          bufferSize = bufferSize,
          level = level,
          header = ZLibParams.Header.GZIP,
        ),
      )
    log4cats
      .Logger[F]
      .trace("GZip middleware encoding content")
      .as(
        resp
          .removeHeader[`Content-Length`]
          .putHeaders(`Content-Encoding`(ContentCoding.gzip))
          .withEntity(Entity.stream(compressPipe(resp.body)))
      )
  }
}
