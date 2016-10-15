package com.example.http4s.blaze

import java.util.concurrent.TimeUnit

import com.example.http4s.ExampleService
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware.Metrics
import org.http4s.dsl._
import org.http4s.util.ProcessApp

import com.codahale.metrics._
import com.codahale.metrics.json.MetricsModule

import com.fasterxml.jackson.databind.ObjectMapper

object BlazeMetricsExample extends ProcessApp {

  val metrics = new MetricRegistry()
  val mapper = new ObjectMapper()
                  .registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, true))

  val metricsService = HttpService {
    case GET -> Root =>
      val writer = mapper.writerWithDefaultPrettyPrinter()
      Ok(writer.writeValueAsString(metrics))
  }

  val srvc = Router(
    "" -> Metrics.meter(metrics, "Sample")(ExampleService.service),
    "/metrics" -> metricsService
  )

  def main(args: List[String]) = BlazeBuilder.bindHttp(8080)
    .mountService(srvc, "/http4s")
    .process
}
