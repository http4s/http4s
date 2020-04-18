package com.example.http4s.tomcat

import cats.effect._
import com.example.http4s.ssl
import org.http4s.server.Server
import org.http4s.server.tomcat.TomcatBuilder

object TomcatSslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    TomcatSslExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object TomcatSslExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): TomcatBuilder[F] =
    TomcatExampleApp
      .builder[F](blocker)
      .bindHttp(8443)
      .withSSL(ssl.storeInfo, ssl.keyManagerPassword)

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server[F]] =
    for {
      blocker <- Blocker[F]
      server <- builder[F](blocker).resource
    } yield server
}
