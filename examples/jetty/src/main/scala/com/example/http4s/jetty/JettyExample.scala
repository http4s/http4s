package com.example.http4s
package jetty

import javax.servlet._

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.servlets.MetricsServlet
import org.http4s.server.jetty.JettyBuilder
import org.http4s.util.ProcessApp

object JettyExample extends ProcessApp {
  val metrics = new MetricRegistry

  def main(args: List[String]) = JettyBuilder
    .bindHttp(8080)
    .withMetricRegistry(metrics)
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new MetricsServlet(metrics), "/metrics/*")
    .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
    .process
}
