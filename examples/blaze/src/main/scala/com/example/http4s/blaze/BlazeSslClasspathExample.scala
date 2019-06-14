package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import com.example.http4s.ssl
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

object BlazeSslClasspathExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslClasspathExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object BlazeSslClasspathExampleApp {

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server[F]] =
    for {
      blocker <- Blocker[F]
      context <- Resource.liftF(
        ssl.loadContextFromClasspath[F](ssl.keystorePassword, ssl.keyManagerPassword))
      server <- BlazeServerBuilder[F]
        .bindHttp(8443)
        .withSSLContext(context)
        .withHttpApp(BlazeExampleApp.httpApp[F](blocker))
        .resource
    } yield server

}
