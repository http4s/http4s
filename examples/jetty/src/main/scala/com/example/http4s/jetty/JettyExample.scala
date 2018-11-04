package com.example.http4s
package jetty

import cats.effect._
import cats.implicits._
import com.codahale.metrics.MetricRegistry
import org.http4s.metrics.dropwizard._
import org.http4s.server.HttpMiddleware
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.middleware.Metrics

object JettyExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    JettyExampleApp.builder[IO].serve.compile.drain.as(ExitCode.Success)
}

object JettyExampleApp {

  def builder[F[_]: ConcurrentEffect: Timer: ContextShift]: JettyBuilder[F] = {
    val metricsRegistry: MetricRegistry = new MetricRegistry
    val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))

    JettyBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(new ExampleService[F].routes), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics")
      .mountFilter(NoneShallPass, "/black-knight/*")
  }

}
