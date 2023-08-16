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
import com.comcast.ip4s._
import fs2.io.net.Network
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server._
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    ExampleApp.serverStream[IO].useForever.as(ExitCode.Success)
}

object ExampleApp {

  def serverStream[F[_]: Async: Network]: Resource[F, Server] =
    EmberServerBuilder.default
      .withPort(port"8080")
      .withHost(host"0.0.0.0")
      .withLoggerFactory(Slf4jFactory.create[F])
      .withHttpApp(new ExampleRoutes[F].routes.orNotFound)
      .build

}

final case class ExampleRoutes[F[_]: Sync]() extends Http4sDsl[F] {
  val routes: HttpRoutes[F] =
    HttpRoutes.of[F] { case GET -> Root / "ping" =>
      Ok("ping")
    }
}
