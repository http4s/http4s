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
  def sslContext[F[_]: Sync] =
    ssl.loadContextFromClasspath(ssl.keystorePassword, ssl.keyManagerPassword)

  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): F[JettyBuilder[F]] =
    sslContext.map { sslCtx =>
      JettyExampleApp
        .builder[F](blocker)
        .bindHttp(8443)
        .withSslContext(sslCtx)
    }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      b <- Resource.liftF(builder[F](blocker))
      server <- b.resource
    } yield server
}
