package com.example.http4s.tomcat

import cats.effect._
import cats.implicits._
import com.example.http4s.ssl
import org.http4s.server.tomcat.TomcatBuilder

object TomcatSslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    TomcatSslExampleApp.builder[IO].serve.compile.drain.as(ExitCode.Success)
}

object TomcatSslExampleApp {

  def builder[F[_]: ConcurrentEffect: ContextShift: Timer]: TomcatBuilder[F] =
    TomcatExampleApp
      .builder[F]
      .bindHttp(8443)
      .withSSL(ssl.storeInfo, ssl.keyManagerPassword)

}
