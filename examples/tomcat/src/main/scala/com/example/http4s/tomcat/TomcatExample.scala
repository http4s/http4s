package com.example.http4s.tomcat

import cats.effect._
import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import fs2._
import org.http4s.server.HttpMiddleware
import org.http4s.server.metrics._
import org.http4s.server.tomcat.TomcatBuilder
import org.http4s.util.StreamApp
import org.http4s.util.ExitCode

object TomcatExample extends TomcatExampleApp[IO]

class TomcatExampleApp[F[_]: Effect] extends StreamApp[F] {
  val metricsRegistry: MetricRegistry = new MetricRegistry
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] =
    Scheduler(corePoolSize = 2).flatMap { implicit scheduler =>
      TomcatBuilder[F]
        .bindHttp(8080)
        .mountService(metrics(new ExampleService[F].service), "/http4s")
        .mountService(metricsService(metricsRegistry), "/metrics/*")
        .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
        .serve
    }
}
