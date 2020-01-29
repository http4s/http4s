package com.example.http4s
package blaze

import cats.effect._
import cats.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

object BlazeSslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object BlazeSslExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer]: BlazeServerBuilder[F] =
    BlazeServerBuilder[F]
      .bindHttp(8443)
      .withSSL(ssl.storeInfo, ssl.keyManagerPassword)

  def sslContext =
    SSLContext

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server[F]] =
    for {
      blocker <- Blocker[F]
      server <- builder[F].withHttpApp(BlazeExampleApp.httpApp(blocker)).resource
    } yield server
}
