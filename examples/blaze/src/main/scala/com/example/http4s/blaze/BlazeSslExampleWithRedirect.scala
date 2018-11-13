package com.example.http4s
package blaze

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.server.blaze.BlazeServerBuilder

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
    BlazeServerBuilder[F]
      .bindHttp(8080)
      .withHttpApp(ssl.redirectApp(8443))
      .serve

  def sslStream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] =
    BlazeSslExampleApp.builder[F].serve

}
