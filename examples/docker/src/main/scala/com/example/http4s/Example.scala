package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import fs2.Stream
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.syntax.kleisli._
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    ExampleApp.serverStream[IO].compile.drain.as(ExitCode.Success)
}

object ExampleApp {

  def serverStream[F[_]: ConcurrentEffect: Timer]: Stream[F, ExitCode] =
    BlazeServerBuilder[F]
      .bindHttp(port = 8080, host = "0.0.0.0")
      .withHttpApp(ExampleRoutes[F]().routes.orNotFound)
      .serve

}

case class ExampleRoutes[F[_]: Sync]() extends Http4sDsl[F] {

  val routes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "ping" => Ok("ping")
    }

}
