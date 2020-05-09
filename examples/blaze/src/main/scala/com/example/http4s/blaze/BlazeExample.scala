package com.example.http4s.blaze

import cats.effect._
import com.example.http4s.ExampleService
import org.http4s.HttpApp
import org.http4s.server.{Router, Server}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import scala.concurrent.ExecutionContext.global

object BlazeExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object BlazeExampleApp {
  def httpApp[F[_]: Effect: ContextShift: Timer](blocker: Blocker): HttpApp[F] =
    Router(
      "/http4s" -> ExampleService[F](blocker).routes
    ).orNotFound

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      app = httpApp[F](blocker)
      server <- BlazeServerBuilder[F](global)
        .bindHttp(8080)
        .withHttpApp(app)
        .resource
    } yield server
}
