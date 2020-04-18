package com.example.http4s
package blaze

import cats.effect._
import cats.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object BlazeSslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object BlazeSslExampleApp {
  def context[F[_]: Sync] =
    ssl.loadContextFromClasspath(ssl.keystorePassword, ssl.keyManagerPassword)

  def builder[F[_]: ConcurrentEffect: ContextShift: Timer]: F[BlazeServerBuilder[F]] =
    context.map { sslContext =>
      BlazeServerBuilder[F]
        .bindHttp(8443)
        .withSslContext(sslContext)
    }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      b <- Resource.liftF(builder[F])
      server <- b.withHttpApp(BlazeExampleApp.httpApp(blocker)).resource
    } yield server
}
