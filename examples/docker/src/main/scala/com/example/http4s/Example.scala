package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder

object Example extends IOApp with Http4sDsl[IO] {
  val service: HttpApp[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ping" => Ok("ping")
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(service)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
