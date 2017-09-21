package com.example.http4s.blaze

import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.StreamApp

object Example extends StreamApp[IO] with Http4sDsl[IO] {
  val service = HttpService[IO] {
    case GET -> Root / "ping" => Ok("ping")
  }

  def stream(args: List[String], requestShutdown: IO[Unit]) =
    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(service)
      .serve
}
