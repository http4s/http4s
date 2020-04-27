package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import com.example.http4s.ExampleService
import fs2._
import org.http4s.HttpApp
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import scala.concurrent.ExecutionContext.global

object BlazeExample extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    BlazeExampleApp.stream[IO].compile.drain.as(ExitCode.Success)

}

object BlazeExampleApp {

  def httpApp[F[_]: Effect: ContextShift: Timer]: HttpApp[F] =
    Router(
      "/http4s" -> ExampleService[F].routes
    ).orNotFound

  def stream[F[_]: ConcurrentEffect: Timer: ContextShift]: Stream[F, ExitCode] =
    BlazeServerBuilder[F](global)
      .bindHttp(8080)
      .withHttpApp(httpApp[F])
      .serve
}
