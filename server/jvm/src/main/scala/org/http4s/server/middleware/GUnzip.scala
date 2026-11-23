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

import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.eq._
import fs2.compression.Compression
import fs2.compression.InflateParams
import org.http4s.headers.`Content-Encoding`
import org.http4s.headers.`Content-Length`

object GUnzip {

  def apply[F[_], G[_]: Sync](
      http: Http[F, G],
      inflateParams: InflateParams = InflateParams.DEFAULT): Http[F, G] =
    Kleisli { (req: Request[G]) =>
      req.headers.get[`Content-Encoding`] match {
        case Some(ce) if isGzipped(ce.contentCoding) =>
          http(gunzipRequest(req, inflateParams))
        case _ => http(req)
      }
    }

  private def isGzipped(cc: ContentCoding): Boolean =
    cc === ContentCoding.gzip || cc === ContentCoding.`x-gzip`

  private def gunzipRequest[F[_]: Sync](
      req: Request[F],
      inflateParams: InflateParams
  ): Request[F] = {
    val newBody = req.body.through(Compression[F].gunzip(inflateParams)).flatMap(_.content)
    val newHeaders = req.removeHeader[`Content-Encoding`].putHeaders(`Content-Length`(69)).headers
    Request[F](
      method = req.method,
      uri = req.uri,
      httpVersion = req.httpVersion,
      headers = newHeaders,
      body = newBody,
      attributes = req.attributes
    )
  }

}
