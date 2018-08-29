package com.example.http4s
package blaze

import cats.effect._
import com.example.http4s.ssl.SslExample
import org.http4s.server.blaze.BlazeBuilder

class BlazeSslExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends SslExample[IO] with IOApp {
  def builder: BlazeBuilder[IO] = BlazeBuilder[IO]

  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}
