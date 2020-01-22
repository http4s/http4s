package com.example.http4s.jetty

import cats.effect._
import cats.implicits._
import com.example.http4s.ssl
import org.http4s.server.Server
import org.http4s.server.jetty.JettyBuilder

object JettySslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    JettySslExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object JettySslExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): JettyBuilder[F] =
    JettyExampleApp
      .builder[F](blocker)
      .bindHttp(8443)
      .withSSL(ssl.storeInfo, ssl.keyManagerPassword)

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server[F]] =
    for {
      blocker <- Blocker[F]
      server <- builder[F](blocker).resource
    } yield server
}
