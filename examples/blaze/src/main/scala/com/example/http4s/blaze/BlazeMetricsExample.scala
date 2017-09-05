package com.example.http4s.blaze

import cats.effect._
import com.codahale.metrics._
import com.example.http4s.ExampleService
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.metrics._
import org.http4s.util.StreamApp

object BlazeMetricsExample extends StreamApp[IO] {
  val metricsRegistry = new MetricRegistry()
  val metrics = Metrics[IO](metricsRegistry)

  val srvc = Router(
    "" -> metrics(ExampleService.service),
    "/metrics" -> metricsService[IO](metricsRegistry)
  )

  def stream(args: List[String], requestShutdown: IO[Unit]) =
    BlazeBuilder[IO]
      .bindHttp(8080)
      .mountService(srvc, "/http4s")
      .serve
}
