package com.example.http4s
package blaze

import cats.effect._
import fs2._
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext.global

object BlazeSslExampleWithRedirect extends IOApp {
  import BlazeSslExampleWithRedirectApp._

  override def run(args: List[String]): IO[ExitCode] =
    sslStream[IO]
      .mergeHaltBoth(redirectStream[IO])
      .compile
      .drain
      .as(ExitCode.Success)
}

object BlazeSslExampleWithRedirectApp {
  def redirectStream[F[_]: ConcurrentEffect: Timer]: Stream[F, ExitCode] =
    BlazeServerBuilder[F](global)
      .bindHttp(8080)
      .withHttpApp(ssl.redirectApp(8443))
      .serve

  def sslStream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] =
    Stream.eval(BlazeSslExampleApp.builder[F]).flatMap(_.serve)
}
