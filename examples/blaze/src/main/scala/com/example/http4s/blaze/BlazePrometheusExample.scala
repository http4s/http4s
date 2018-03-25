package com.example.http4s.blaze

import java.util.concurrent.TimeUnit

import com.example.http4s.ExampleService
import org.http4s._
import org.http4s.server.ServerApp
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.prometheus.server.{Metrics, exportService}
import org.http4s.dsl._

/**
 * Hit http://localhost:8080/http4s/ping or other routes from ExampleService.
 *
 * Then go to http://localhost:8080/http4s/metrics/ to see a
 * Prometheus-scrapable endpoint.
 */
object BlazePrometheusExample extends ServerApp {
  val service = Router(
    "" -> Metrics("example")(ExampleService.service),
    "/metrics" -> exportService()
  )

  def server(args: List[String]) = BlazeBuilder.bindHttp(8080)
    .mountService(service, "/http4s")
    .start
}
