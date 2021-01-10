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

import scala.concurrent.duration._

object EmberServerSimpleExample extends IOApp {

//  (Stream.repeatEval(IO(5)).interruptAfter(5.seconds) ++ Stream.repeatEval(IO(10)).interruptAfter(5.seconds))
//    .evalMap(i => IO(println(i)))

  val resource: Resource[IO, Unit] = Resource.make(IO(println("A")))(_ => IO(println("B")))

  val stream: Stream[IO, Unit] =
    Stream
      .resource(resource)
      .flatMap { _ =>
        def go: Stream[IO, Unit] =
          Stream.eval[IO, Unit](IO(println("tick"))).delayBy(1.seconds) ++ go
        go.interruptAfter(5.seconds)
      }
      .prefetch

  def run2(args: List[String]): IO[ExitCode] =
    stream
//      .map(_ => Stream.never[IO])
      .map(_ => Stream.eval(IO(println("Finished"))).delayBy(5.seconds))
      .parJoinUnbounded
      .drain
      .compile
      .drain
      .void
      .as(ExitCode.Success)

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
      IO.sleep(5.seconds).flatTap(_ => IO(println("going to close shortly"))).as(ExitCode.Success))

  def service[F[_]](implicit F: Async[F], timer: Timer[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    import scala.concurrent.duration._

    HttpRoutes
      .of[F] {
        case req @ POST -> Root =>
          for {
            json <- req.decodeJson[Json]
            resp <- Ok(json)
          } yield resp
        case GET -> Root =>
          timer.sleep(15.seconds) >> Ok(Json.obj("root" -> Json.fromString("GET")))
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
