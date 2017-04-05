package com.example.http4s.tomcat

import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import org.http4s.server.ServerApp
import org.http4s.server.metrics._
import org.http4s.server.tomcat.TomcatConfig

object TomcatExample extends ServerApp {
  val metrics = new MetricRegistry

  def server(args: List[String]) = TomcatConfig.default
    .bindHttp(8080)
    .mountService(Metrics(metrics)(ExampleService.service), "/http4s")
    .mountService(metricsService(metrics), "/metrics/*")
    .start
}
