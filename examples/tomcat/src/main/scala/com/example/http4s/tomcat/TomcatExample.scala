package com.example.http4s.tomcat

import cats.effect._
import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import org.http4s.metrics.dropwizard.Dropwizard
import org.http4s.server.{HttpMiddleware, Server}
import org.http4s.metrics.dropwizard._
import org.http4s.server.middleware.Metrics
import org.http4s.server.tomcat.TomcatBuilder

object TomcatExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    TomcatExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object TomcatExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): TomcatBuilder[F] = {
    val metricsRegistry: MetricRegistry = new MetricRegistry
    val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))

    TomcatBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(ExampleService[F](blocker).routes), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics/*")
      .mountFilter(NoneShallPass, "/black-knight/*")
  }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server[F]] =
    for {
      blocker <- Blocker[F]
      server <- builder[F](blocker).resource
    } yield server
}
