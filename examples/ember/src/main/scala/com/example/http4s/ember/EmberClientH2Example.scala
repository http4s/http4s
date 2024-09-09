/*
 * Copyright 2020 http4s.org
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

package com.example.http4s.ember

import cats._
import cats.effect._
import cats.syntax.all._
import fs2.io.net._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.h2.H2Keys.Http2PriorKnowledge
import org.http4s.implicits._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object EmberClientH2Example extends IOApp {

  object ClientTest {
    def printPushPromiseSupport[F[_]: Async]
        : (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]] = {
      case (req, fResp) =>
        Sync[F].delay(println(s"Push Promise: $req")) >>
          fResp
            .flatMap(resp =>
              resp.bodyText.compile.string.flatMap(_ =>
                Sync[F].delay(println(s"Push Promise Resp:($req, $resp)"))
              )
            )
            .as(Outcome.succeeded(Applicative[F].unit))
    }

    def noopPushPromiseSupport[F[_]: Applicative]
        : (Request[fs2.Pure], F[Response[F]]) => F[Outcome[F, Throwable, Unit]] = { case (_, _) =>
      Applicative[F].pure(Outcome.succeeded(Applicative[F].unit))
    }

    def test[F[_]: Async: Network]: F[Unit] =
      Resource
        .eval(Network[F].tlsContext.insecure)
        .flatMap { tls =>
          implicit val loggerFactory: LoggerFactory[F] =
            Slf4jFactory.create[F]

          EmberClientBuilder
            .default[F]
            .withHttp2
            .withTLSContext(tls)
            .withLogger(org.typelevel.log4cats.slf4j.Slf4jLogger.getLogger[F])
            .withPushPromiseSupport(printPushPromiseSupport)
            .build
        }
        .use { c =>
          val p = c
            .run(
              org.http4s
                .Request[F](
                  org.http4s.Method.GET,
                  // uri = uri"https://github.com/"
                  // uri = uri"https://en.wikipedia.org/wiki/HTTP/2"
                  // uri = uri"https://twitter.com/"
                  // uri = uri"https://banno.com/"
                  // uri = uri"http://http2.golang.org/reqinfo"
                  // uri = uri"http://localhost:8080/push-promise",
                  uri = uri"http://localhost:8080/trailers",
                  // uri = uri"https://www.nikkei.com/" // PUSH PROMISES
                )
                .withAttribute(Http2PriorKnowledge, ())
                .putHeaders(Headers("trailers" -> "x-test-client"))
                .withTrailerHeaders(Headers("x-test-client" -> "client-info").pure[F])
            )
            .use(resp =>
              resp.body.compile.drain >> resp.trailerHeaders
                .flatMap(h => Sync[F].delay(println(s"Resp $resp: trailers: $h")))
            )
          p
        }
  }

  def run(args: List[String]): IO[ExitCode] =
    ClientTest
      .test[IO]
      .as(ExitCode.Success)
}
