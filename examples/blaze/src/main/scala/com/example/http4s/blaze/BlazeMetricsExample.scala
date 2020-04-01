package com.example.http4s.blaze

import cats.effect._
import cats.implicits._
import com.codahale.metrics.{Timer => _, _}
import com.example.http4s.ExampleService
import org.http4s.HttpApp
import org.http4s.implicits._
import org.http4s.metrics.dropwizard._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Metrics
import org.http4s.server.{HttpMiddleware, Router, Server}

class BlazeMetricsExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeMetricsExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object BlazeMetricsExampleApp {
  def httpApp[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): HttpApp[F] = {
    val metricsRegistry: MetricRegistry = new MetricRegistry()
    val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))
    Router(
      "/http4s" -> metrics(ExampleService[F](blocker).routes),
      "/http4s/metrics" -> metricsService[F](metricsRegistry)
    ).orNotFound
  }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      app = httpApp[F](blocker)
      server <- BlazeServerBuilder[F]
        .bindHttp(8080)
        .withHttpApp(app)
        .resource
    } yield server
}
