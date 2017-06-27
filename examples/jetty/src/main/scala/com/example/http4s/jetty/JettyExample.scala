package com.example.http4s
package jetty

import cats.effect._
import com.codahale.metrics.MetricRegistry
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.metrics._
import org.http4s.util.StreamApp

object JettyExample extends StreamApp[IO] {
  val metricsRegistry = new MetricRegistry
  val metrics = Metrics[IO](metricsRegistry)

  def stream(args: List[String]) = JettyBuilder[IO]
    .bindHttp(8080)
    .mountService(metrics(ExampleService.service), "/http4s")
    .mountService(metricsService(metricsRegistry), "/metrics")
    .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
    .serve
}
