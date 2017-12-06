package com.example.http4s.blaze

import cats.effect._
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder

object Example extends StreamApp[IO] with Http4sDsl[IO] {
  val service: HttpService[IO] = HttpService[IO] {
    case GET -> Root / "ping" => Ok("ping")
  }

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(service)
      .serve
}
