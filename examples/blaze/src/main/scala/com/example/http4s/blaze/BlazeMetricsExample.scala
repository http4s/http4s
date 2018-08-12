package com.example.http4s.blaze

import cats.effect._
import com.codahale.metrics.{Timer => _, _}
import com.example.http4s.ExampleService
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.HttpRoutes
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.metrics._
import org.http4s.server.{HttpMiddleware, Router}
import scala.concurrent.ExecutionContext.Implicits.global

object BlazeMetricsExample extends BlazeMetricsExampleApp[IO]

class BlazeMetricsExampleApp[F[_]: ConcurrentEffect] extends StreamApp[F] {
  val metricsRegistry: MetricRegistry = new MetricRegistry()
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def service(implicit timer: Timer[F]): HttpRoutes[F] =
    Router(
      "" -> metrics(new ExampleService[F].service),
      "/metrics" -> metricsService[F](metricsRegistry)
    )

  def stream(args: List[String], requestShutdown: F[Unit]): fs2.Stream[F, ExitCode] = {
    implicit val timer = Timer.derive[F]
    BlazeBuilder[F]
      .bindHttp(8080)
      .mountService(service, "/http4s")
      .serve
  }
}
