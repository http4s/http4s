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

import fs2._
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import _root_.io.circe._
import _root_.org.http4s.ember.server.EmberServerBuilder

object EmberServerSimpleExample extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val host = "0.0.0.0"
    val port = 8080
    for {
      // Server Level Resources Here
      server <-
        EmberServerBuilder
          .default[IO]
          .withHost(host)
          .withPort(port)
          .withHttpApp(service[IO])
          .build
    } yield server
  }.use(server =>
    IO.delay(println(s"Server Has Started at ${server.address}")) >>
      IO.never.as(ExitCode.Success))

  def service[F[_]: Sync]: HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case req @ POST -> Root =>
          for {
            json <- req.decodeJson[Json]
            resp <- Ok(json)
          } yield resp
        case GET -> Root =>
          Ok(Json.obj("root" -> Json.fromString("GET")))
        case GET -> Root / "hello" / name =>
          Ok(show"Hi $name!")
        case GET -> Root / "chunked" =>
          val body = Stream("This IS A CHUNK\n")
            .covary[F]
            .repeat
            .take(100)
            .through(fs2.text.utf8Encode[F])
          Ok(body).map(_.withContentType(headers.`Content-Type`(MediaType.text.plain)))
      }
      .orNotFound
  }
}
