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

import cats.Functor
import cats.data.Kleisli
import cats.syntax.all._
import fs2.compression._
import org.http4s.headers._

object GZip extends GZipPlatform {
  private[this] val logger = Platform.loggerFactory.getLogger

  // TODO: It could be possible to look for F.pure type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply[F[_]: Functor, G[_]: Compression](
      http: Http[F, G],
      bufferSize: Int = 32 * 1024,
      level: DeflateParams.Level = DeflateParams.Level.DEFAULT,
      isZippable: Response[G] => Boolean = defaultIsZippable[G](_: Response[G]),
  ): Http[F, G] =
    Kleisli { (req: Request[G]) =>
      val unzippedRequest = unzipOrPass(req, bufferSize)
      unzippedRequest.headers.get[`Accept-Encoding`] match {
        case Some(acceptEncoding) if satisfiedByGzip(acceptEncoding) =>
          http(unzippedRequest).map(zipOrPass(_, bufferSize, level, isZippable))
        case _ => http(unzippedRequest)
      }
    }

  def defaultIsZippable[F[_]](resp: Response[F]): Boolean = {
    val contentType = resp.headers.get[`Content-Type`]
    !resp.headers.contains[`Content-Encoding`] &&
    resp.status.isEntityAllowed &&
    (contentType.isEmpty || contentType.get.mediaType.compressible ||
      (contentType.get.mediaType == MediaType.application.`octet-stream`))
  }

  private def isZipped[F[_]](req: Request[F]): Boolean =
    req.headers.get[`Content-Encoding`].map(_.contentCoding).exists { coding =>
      coding === ContentCoding.gzip || coding === ContentCoding.`x-gzip`
    }

  private def satisfiedByGzip(acceptEncoding: `Accept-Encoding`) =
    acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
      ContentCoding.`x-gzip`
    )

  private def zipOrPass[F[_]: Compression](
      response: Response[F],
      bufferSize: Int,
      level: DeflateParams.Level,
      isZippable: Response[F] => Boolean,
  ): Response[F] =
    response match {
      case resp if isZippable(resp) => zipResponse(bufferSize, level, resp)
      case resp => resp // Don't touch it, Content-Encoding already set
    }

  private def unzipOrPass[F[_]: Compression](
      request: Request[F],
      bufferSize: Int,
  ): Request[F] =
    request match {
      case req if isZipped(req) => unzipRequest(bufferSize, req)
      case req => req
    }

  private def zipResponse[F[_]: Compression](
      bufferSize: Int,
      level: DeflateParams.Level,
      resp: Response[F],
  ): Response[F] = {
    val compressPipe =
      Compression[F].gzip(
        fileName = None,
        modificationTime = None,
        comment = None,
        DeflateParams(
          bufferSize = bufferSize,
          level = level,
          header = ZLibParams.Header.GZIP,
        ),
      )
    logger.trace("GZip middleware encoding content").unsafeRunSync()
    resp
      .removeHeader[`Content-Length`]
      .putHeaders(`Content-Encoding`(ContentCoding.gzip))
      .pipeBodyThrough(compressPipe)
  }

  private def unzipRequest[F[_]: Compression](
      bufferSize: Int,
      req: Request[F],
  ): Request[F] = {
    val decompressPipe = Compression[F].gunzip(bufferSize = bufferSize).andThenF(_.content)
    logger.trace("GZip middleware decoding content").unsafeRunSync()
    req
      .removeHeader[`Content-Length`]
      .removeHeader[`Content-Encoding`]
      .pipeBodyThrough(decompressPipe)
  }
}
