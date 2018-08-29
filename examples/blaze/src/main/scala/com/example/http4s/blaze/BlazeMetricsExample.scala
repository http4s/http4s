package com.example.http4s.blaze

import cats.effect._
import com.codahale.metrics.{Timer => _, _}
import com.example.http4s.ExampleService
import org.http4s.HttpRoutes
import org.http4s.server.{HttpMiddleware, Router}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.metrics._

class BlazeMetricsExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends BlazeMetricsExampleApp[IO] with IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}

class BlazeMetricsExampleApp[F[_]: ConcurrentEffect : ContextShift : Timer] {
  val metricsRegistry: MetricRegistry = new MetricRegistry()
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def service: HttpRoutes[F] =
    Router(
      "" -> metrics(new ExampleService[F].service),
      "/metrics" -> metricsService[F](metricsRegistry)
    )

  def stream: fs2.Stream[F, ExitCode] = {
    BlazeBuilder[F]
      .bindHttp(8080)
      .mountService(service, "/http4s")
      .serve
  }
}
