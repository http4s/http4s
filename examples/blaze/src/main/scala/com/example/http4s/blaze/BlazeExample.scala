package com.example.http4s.blaze

import cats.effect._
import com.example.http4s.ExampleService
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.StreamApp

object BlazeExample extends StreamApp[IO] {
  def stream(args: List[String], requestShutdown: IO[Unit]) =
    BlazeBuilder[IO]
      .bindHttp(8080)
      .mountService(ExampleService.service, "/http4s")
      .serve
}
