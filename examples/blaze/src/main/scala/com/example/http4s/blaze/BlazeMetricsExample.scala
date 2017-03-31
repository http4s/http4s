package com.example.http4s.blaze

import com.codahale.metrics._
import com.example.http4s.ExampleService
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.metrics._
import org.http4s.util.ProcessApp

object BlazeMetricsExample extends ProcessApp {
  val metricRegistry = new MetricRegistry()

  val srvc = Router(
    "" -> Metrics(metricRegistry)(ExampleService.service),
    "/metrics" -> metricsService(metricRegistry)
  )

  def main(args: List[String]) = BlazeBuilder.bindHttp(8080)
    .mountService(srvc, "/http4s")
    .serve
}
