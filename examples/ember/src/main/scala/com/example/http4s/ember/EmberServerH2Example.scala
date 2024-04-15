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
import cats.effect.std.Console
import cats.syntax.all._
import com.comcast.ip4s._
import com.example.http4s.ssl
import fs2._
import fs2.io.net._
import fs2.io.net.tls._
import org.http4s._
import org.http4s.dsl._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

object EmberServerH2Example extends IOApp {

  object ServerTest {

    val resp = Response[fs2.Pure](Status.Ok).withEntity("Hello World!")
    def simpleApp[F[_]: Concurrent: Console] = {
      val dsl = new Http4sDsl[F] {}; import dsl._
      HttpRoutes
        .of[F] {
          case _ -> Root / "foo" =>
            // println(req)
            Response[F](Status.Ok).withEntity("Foo Endpoint").pure[F]
          case _ -> Root / "stream" =>
            // println("Got stream endpoint")
            Ok(Stream("Ok").covary[F])
              .flatTap(Console[F].println)
          case req @ _ -> Root / "trailers" =>
            println(s"Got $req at trailers endpoint")
            req.headers
              .get[org.http4s.headers.Trailer]
              .fold(Applicative[F].unit)(_ =>
                req.body.compile.drain >> req.trailerHeaders
                  .flatMap(h => Console[F].println(s"Incomming Trailers: $h"))
              ) >>
              Response[F](Status.Ok)
                .withEntity("Trailers Incomming!")
                .putHeaders("trailer" -> "x-my-data")
                .withTrailerHeaders(Headers("x-my-data" -> "Hello from trailers!").pure[F])
                .pure[F]
          case _ -> Root / "push-promise" =>
            resp
              .covary[F] // URI needs authority scheme, etc
              .withAttribute(
                org.http4s.ember.core.h2.H2Keys.PushPromises,
                Request[Pure](Method.GET, uri"https://localhost:8080/foo") :: Nil,
              )
              .pure[F]

        }
        .orNotFound
    }

    def testALPN[F[_]: Async: Console: Network] = for {
      sslContext <- Resource.eval(
        ssl.loadContextFromClasspath(ssl.keystorePassword, ssl.keyManagerPassword)
      )
      tlsContext = Network[F].tlsContext.fromSSLContext(sslContext)
      _ <- EmberServerBuilder
        .default[F]
        .withTLS(tlsContext, TLSParameters.Default)
        .withHttp2
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8081")
        .withHttpApp(simpleApp)
        .withErrorHandler { case error =>
          Console[F]
            .println(s"Unexpected error:$error")
            .as(Response(status = Status.InternalServerError))
        }
        .build
    } yield ()

    // Can Test Both http2-prior-knowledge, and h2c
    def testCleartext[F[_]: Async: Console: Network] =
      EmberServerBuilder
        .default[F]
        .withHttp2
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(simpleApp)
        .withErrorHandler { case error =>
          Console[F]
            .println(s"Unexpected error:$error")
            .as(Response(status = Status.InternalServerError))
        }
        .build

  }

  implicit val C: Console[IO] = Console.make[IO]
  def run(args: List[String]): IO[ExitCode] =
    (
      ServerTest.testCleartext[IO],
      ServerTest.testALPN[IO],
    ).tupled.useForever
      .as(ExitCode.Success)

}
