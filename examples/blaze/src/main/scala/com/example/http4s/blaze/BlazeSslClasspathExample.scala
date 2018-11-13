package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import com.example.http4s.ssl
import fs2._
import org.http4s.server.blaze.BlazeServerBuilder

object BlazeSslClasspathExample extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslClasspathExampleApp.stream[IO].compile.drain.as(ExitCode.Success)

}

object BlazeSslClasspathExampleApp {

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] =
    for {
      context <- Stream.eval(
        ssl.loadContextFromClasspath[F](ssl.keystorePassword, ssl.keyManagerPassword))
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(8443)
        .withSSLContext(context)
        .withHttpApp(BlazeExampleApp.httpApp[F])
        .serve
    } yield exitCode

}
