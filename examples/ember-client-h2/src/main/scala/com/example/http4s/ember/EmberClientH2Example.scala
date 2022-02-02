/*
 * Copyright 2021 http4s.org
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
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.h2._
import org.http4s.implicits._

object EmberClientH2Example extends IOApp {

  object ClientTest {

    def test[F[_]: Async: Parallel] =
      Resource
        .eval(Network[F].tlsContext.system)
        .flatMap { tls =>
          EmberClientBuilder
            .default[F]
            .withHttp2
            .withTLSContext(tls)
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
                  uri = uri"http://localhost:8080/foo",
                  // uri = uri"https://www.nikkei.com/" // PUSH PROMISES
                )
                .withAttribute(H2Keys.Http2PriorKnowledge, ())
            )
            .use(resp => resp.body.compile.drain >> Sync[F].delay(println(s"Resp $resp")))
          p
        }
  }

  def run(args: List[String]): IO[ExitCode] =
    ClientTest
      .test[IO]
      .as(ExitCode.Success)
}
