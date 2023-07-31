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

import _root_.io.circe.Json
import _root_.org.http4s.ember.client.EmberClientBuilder
import _root_.org.typelevel.log4cats.Logger
import _root_.org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.effect._
import cats.syntax.all._
import fs2._
import org.http4s._
import org.http4s.circe._
import org.http4s.client._
import org.http4s.implicits._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import scodec.bits.ByteVector

import scala.concurrent.duration._

object EmberClientSimpleExample extends IOApp {

  val githubReq: Request[IO] = Request[IO](Method.GET, uri"http://christopherdavenport.github.io/")
  val dadJokeReq: Request[IO] = Request[IO](Method.GET, uri"https://icanhazdadjoke.com/")
  val googleReq: Request[IO] = Request[IO](Method.GET, uri"https://www.google.com/")
  val httpBinGet: Request[IO] = Request[IO](Method.GET, uri"https://httpbin.org/get")
  val httpBinPng: Request[IO] = Request[IO](Method.GET, uri"https://httpbin.org/image/png")

  val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def run(args: List[String]): IO[ExitCode] =
    EmberClientBuilder
      .default[IO]
      .build
      .use(client =>
        logTimed(
          logger,
          "Http Only - ChristopherDavenport Site",
          getRequestBufferedBody(client, githubReq),
        ) >>
          logTimed(logger, "Json - DadJoke", client.expect[Json](dadJokeReq)) >>
          logTimed(logger, "Https - Google", getRequestBufferedBody(client, googleReq)) >>
          logTimed(
            logger,
            "Small Response - HttpBin Get",
            getRequestBufferedBody(client, httpBinGet),
          ) >>
          logTimed(
            logger,
            "Large Response - HttpBin PNG",
            getRequestBufferedBody(client, httpBinPng),
          ) >>
          IO(println("Done"))
      )
      .as(ExitCode.Success)

  def getRequestBufferedBody[F[_]: Async](client: Client[F], req: Request[F]): F[Response[F]] =
    client
      .run(req)
      .use(resp =>
        resp.body.compile
          .to(ByteVector)
          .map(bv => resp.copy(body = Stream.chunk(Chunk.byteVector(bv))))
      )

  def logTimed[F[_]: Temporal, A](logger: Logger[F], name: String, fa: F[A]): F[A] =
    timedMS(fa).flatMap { case (time, action) =>
      logger.info(s"Action $name took $time").as(action)
    }

  def timedMS[F[_]: Temporal, A](fa: F[A]): F[(FiniteDuration, A)] = {
    val nowMS = Temporal[F].monotonic
    (nowMS, fa, nowMS).mapN { case (before, result, after) =>
      val time = after - before
      (time, result)
    }
  }

}
