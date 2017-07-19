package com.example.http4s.tomcat

import cats.effect.IO
import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import org.http4s.server.metrics._
import org.http4s.server.tomcat.TomcatBuilder
import org.http4s.util.StreamApp

object TomcatExample extends StreamApp[IO] {
  val metricsRegistry = new MetricRegistry
  val metrics = Metrics[IO](metricsRegistry)

  def stream(args: List[String], requestShutdown: IO[Unit]) =
    TomcatBuilder[IO]
      .bindHttp(8080)
      .mountService(metrics(ExampleService.service), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics/*")
      .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
      .serve
}
