package com.example.http4s.blaze

import cats.effect._
import com.example.http4s.ssl.SslClasspathExample
import org.http4s.server.blaze.BlazeBuilder

class BlazeSslClasspathExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends SslClasspathExample[IO] with IOApp {
  def builder: BlazeBuilder[IO] = BlazeBuilder[IO]
  def run(args: List[String]): IO[ExitCode] =
    sslStream.compile.toList.map(_.head)
}

