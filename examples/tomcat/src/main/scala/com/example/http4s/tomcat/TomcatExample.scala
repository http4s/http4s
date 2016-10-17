package com.example.http4s.tomcat

import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import org.http4s.server.ServerApp
import org.http4s.server.metrics._
import org.http4s.server.tomcat.TomcatBuilder

object TomcatExample extends ServerApp {
  val metrics = new MetricRegistry

  def server(args: List[String]) = TomcatBuilder
    .bindHttp(8080)
    .mountService(Metrics(metrics)(ExampleService.service), "/http4s")
    .mountService(metricsService(metrics), "/metrics/*")
    .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
    .start
}
