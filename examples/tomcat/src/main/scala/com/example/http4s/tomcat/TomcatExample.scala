package com.example.http4s.tomcat

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.servlets.MetricsServlet
import com.example.http4s.ExampleService
import org.http4s.server.tomcat.TomcatBuilder
import org.http4s.util.ProcessApp

object TomcatExample extends ProcessApp {
  val metrics = new MetricRegistry

  def main(args: List[String]) = TomcatBuilder
    .bindHttp(8080)
    .withMetricRegistry(metrics)
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new MetricsServlet(metrics), "/metrics/*")
    .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
    .process
}
