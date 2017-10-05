package com.example.http4s
package jetty

import cats.effect._
import com.codahale.metrics.MetricRegistry
import fs2._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.HttpMiddleware
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.metrics._
import org.http4s.util.StreamApp

object JettyExample extends JettyExampleApp[IO]

class JettyExampleApp[F[_]: Effect] extends StreamApp[F] with Http4sDsl[F] {
  val metricsRegistry: MetricRegistry = new MetricRegistry
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, Unit] =
    Scheduler(corePoolSize = 2).flatMap { implicit scheduler =>
      JettyBuilder[F]
        .bindHttp(8080)
        .mountService(metrics(new ExampleService[F].service), "/http4s")
        .mountService(metricsService(metricsRegistry), "/metrics")
        .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
        .serve
    }
}
