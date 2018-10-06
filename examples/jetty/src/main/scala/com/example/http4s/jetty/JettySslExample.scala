package com.example.http4s.jetty

import cats.effect._
import cats.implicits._
import com.example.http4s.ssl
import org.http4s.server.jetty.JettyBuilder

object JettySslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    JettySslExampleApp.builder[IO].serve.compile.drain.as(ExitCode.Success)
}

object JettySslExampleApp {

  def builder[F[_]: ConcurrentEffect: ContextShift: Timer]: JettyBuilder[F] =
    JettyExampleApp
      .builder[F]
      .bindHttp(8443)
      .withSSL(ssl.storeInfo, ssl.keyManagerPassword)

}
