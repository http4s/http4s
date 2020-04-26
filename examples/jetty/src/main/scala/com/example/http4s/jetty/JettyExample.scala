package com.example.http4s
package jetty

import cats.effect._
import com.codahale.metrics.MetricRegistry
import org.http4s.metrics.dropwizard._
import org.http4s.server.{HttpMiddleware, Server}
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.middleware.Metrics

object JettyExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    JettyExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object JettyExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): JettyBuilder[F] = {
    val metricsRegistry: MetricRegistry = new MetricRegistry
    val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))

    JettyBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(ExampleService[F](blocker).routes), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics")
      .mountFilter(NoneShallPass, "/black-knight/*")
  }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      server <- builder[F](blocker).resource
    } yield server
}
