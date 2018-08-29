package com.example.http4s.blaze

import cats.effect._
import com.example.http4s.ExampleService
import org.http4s.server.blaze.BlazeBuilder

class BlazeExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends BlazeExampleApp[IO] with IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}

class BlazeExampleApp[F[_]: ConcurrentEffect : Timer : ContextShift] {
  def stream: fs2.Stream[F, ExitCode] = {
    BlazeBuilder[F]
      .bindHttp(8080)
      .mountService(new ExampleService[F].service, "/http4s")
      .serve
  }
}
