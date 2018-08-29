package com.example.http4s.jetty

import cats.effect._
import com.example.http4s.ssl.SslClasspathExample
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.{ServerBuilder, SSLContextSupport}

class JettySslClasspathExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends SslClasspathExample[IO] with IOApp {
  override def builder: ServerBuilder[IO] with SSLContextSupport[IO] = JettyBuilder[IO]

  override def run(args: List[String]): IO[ExitCode] =
    sslStream.compile.toList.map(_.head)
}
