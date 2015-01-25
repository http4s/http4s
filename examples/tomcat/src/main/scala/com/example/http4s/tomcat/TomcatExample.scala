package com.example.http4s.tomcat

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.servlets.MetricsServlet
import com.example.http4s.ExampleService
import org.http4s.server.tomcat.TomcatBuilder

object TomcatExample extends App {
  val metrics = new MetricRegistry

  TomcatBuilder
    .bindHttp(8080)
    .withMetricRegistry(metrics)
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new MetricsServlet(metrics), "/metrics/*")
    .run
    .awaitShutdown()
}
