package com.example.http4s.blaze

import java.util.concurrent.TimeUnit

import scalaz._, Scalaz._
import com.codahale.metrics._
import com.example.http4s.ExampleService
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.{Router, ServerApp}
import org.http4s.server.blaze.BlazeServerConfig
import org.http4s.server.metrics._

object BlazeMetricsExample extends ServerApp {
  val metricRegistry = new MetricRegistry()

  val srvc = Router(
    "" -> Metrics(metricRegistry)(ExampleService.service),
    "/metrics" -> metricsService(metricRegistry)
  )

  def server(args: List[String]) = BlazeServerConfig.default
    .bindHttp(8080)
    .mountService(srvc, "/http4s")
    .start
}
