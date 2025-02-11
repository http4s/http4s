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

package org.http4s.server.middleware

import cats.ApplicativeThrow
import cats.Functor
import cats.Monad
import cats.data.Kleisli
import cats.syntax.all._
import fs2.Stream
import fs2.compression.Compression
import org.http4s.ContentCoding
import org.http4s.Http
import org.http4s.MalformedMessageBodyFailure
import org.http4s.Request
import org.http4s.headers.`Content-Encoding`
import org.http4s.headers.`Content-Length`
import org.typelevel.log4cats

object GUnzip {

  def apply[F[_]: Monad: log4cats.LoggerFactory, G[_]: ApplicativeThrow: Compression](
      http: Http[F, G],
      bufferSize: Int = 32 * 1024,
  ): Http[F, G] = {
    implicit val logger: log4cats.Logger[F] = log4cats.LoggerFactory[F].getLogger
    Kleisli { (req: Request[G]) =>
      for {
        unzippedRequest <- req match {
          case req if isZipped(req) => unzipRequest[F, G](bufferSize, req)
          case req => req.pure[F]
        }
        response <- http(unzippedRequest)
      } yield response
    }
  }

  private def isZipped[F[_]](req: Request[F]): Boolean =
    req.headers.get[`Content-Encoding`].map(_.contentCoding).exists { coding =>
      coding === ContentCoding.gzip || coding === ContentCoding.`x-gzip`
    }

  private def unzipRequest[F[_]: Functor, G[_]: Compression](
      bufferSize: Int,
      req: Request[G],
  )(implicit logger: log4cats.Logger[F], G: ApplicativeThrow[G]): F[Request[G]] = {
    val decompressPipe = Compression[G].gunzip(bufferSize = bufferSize).andThenF(_.content).andThen {
      _.handleErrorWith { e =>
        Stream.eval(
          G.raiseError(
            MalformedMessageBodyFailure(
              "Failed to decode gzippped request body",
              Some(e),
            )
          )
        )
      }
    }
    val response = req
      .removeHeader[`Content-Length`]
      .removeHeader[`Content-Encoding`]
      .pipeBodyThrough(decompressPipe)
    logger.trace("GUnzip middleware decoding content").as(response)
  }
}
