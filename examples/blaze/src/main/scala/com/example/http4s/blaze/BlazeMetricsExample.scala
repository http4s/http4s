package com.example.http4s.blaze

import cats.effect._
import com.codahale.metrics._
import com.example.http4s.ExampleService
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.HttpService
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.metrics._
import org.http4s.server.{HttpMiddleware, Router}
import scala.concurrent.ExecutionContext.Implicits.global

object BlazeMetricsExample extends BlazeMetricsExampleApp[IO]

class BlazeMetricsExampleApp[F[_]: Effect] extends StreamApp[F] {
  val metricsRegistry: MetricRegistry = new MetricRegistry()
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def service(implicit scheduler: Scheduler): HttpService[F] =
    Router(
      "" -> metrics(new ExampleService[F].service),
      "/metrics" -> metricsService[F](metricsRegistry)
    )

  def stream(args: List[String], requestShutdown: F[Unit]): fs2.Stream[F, ExitCode] =
    for {
      scheduler <- Scheduler(corePoolSize = 2)
      exitCode <- BlazeBuilder[F]
        .bindHttp(8080)
        .mountService(service(scheduler), "/http4s")
        .serve
    } yield exitCode
}
