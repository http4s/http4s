package com.example.http4s.blaze

import cats.effect._
import com.example.http4s.ExampleService
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object BlazeExample extends BlazeExampleApp[IO]

class BlazeExampleApp[F[_]: ConcurrentEffect] extends StreamApp[F] {
  def stream(args: List[String], requestShutdown: F[Unit]): fs2.Stream[F, ExitCode] = {
    implicit val timer = Timer.derive[F]
    BlazeBuilder[F]
      .bindHttp(8080)
      .mountService(new ExampleService[F].service, "/http4s")
      .serve
  }
}
