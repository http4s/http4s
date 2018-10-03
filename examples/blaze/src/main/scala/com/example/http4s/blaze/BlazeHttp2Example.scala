package com.example.http4s
package blaze

import cats.effect._
import cats.implicits._

object BlazeHttp2Example extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslExampleApp
      .builder[IO]
      .enableHttp2(true)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
