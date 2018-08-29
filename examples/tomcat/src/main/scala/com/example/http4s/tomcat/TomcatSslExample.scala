package com.example.http4s
package tomcat

import cats.effect._
import com.example.http4s.ssl.SslExample
import org.http4s.server.tomcat.TomcatBuilder

class TomcatSslExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends SslExample[IO] with IOApp {
  def builder: TomcatBuilder[IO] = TomcatBuilder[IO]

  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}
