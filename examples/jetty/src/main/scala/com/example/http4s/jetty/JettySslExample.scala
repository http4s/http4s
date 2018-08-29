package com.example.http4s
package jetty

import cats.effect._
import com.example.http4s.ssl.SslExample
import org.http4s.server.jetty.JettyBuilder

class JettySslExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends SslExample[IO] with IOApp {
  def builder: JettyBuilder[IO] = JettyBuilder[IO]

  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}
