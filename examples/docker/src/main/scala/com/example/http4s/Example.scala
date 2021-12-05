/*
 * Copyright 2017 http4s.org
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

package com.example.http4s.blaze

import cats.effect._
import fs2.Stream
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.Http4sDsl

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    ExampleApp.serverStream[IO].compile.drain.as(ExitCode.Success)
}

object ExampleApp {
  def serverStream[F[_]: Async]: Stream[F, ExitCode] =
    BlazeServerBuilder[F]
      .bindHttp(port = 8080, host = "0.0.0.0")
      .withHttpApp(new ExampleRoutes[F].routes.orNotFound)
      .serve
}

final case class ExampleRoutes[F[_]: Sync]() extends Http4sDsl[F] {
  val routes: HttpRoutes[F] =
    HttpRoutes.of[F] { case GET -> Root / "ping" =>
      Ok("ping")
    }
}
