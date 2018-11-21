package com.example.http4s.blaze

import cats.effect._
import com.codahale.metrics.{Timer => _, _}
import com.example.http4s.ExampleService
import org.http4s.HttpApp
import org.http4s.implicits._
import org.http4s.metrics.dropwizard._
import org.http4s.server.{HttpMiddleware, Router}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Metrics

class BlazeMetricsExample(implicit timer: Timer[IO], ctx: ContextShift[IO])
    extends BlazeMetricsExampleApp[IO]
    with IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}

class BlazeMetricsExampleApp[F[_]: ConcurrentEffect: ContextShift: Timer] {
  val metricsRegistry: MetricRegistry = new MetricRegistry()
  val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))

  def app: HttpApp[F] =
    Router(
      "/http4s" -> metrics(new ExampleService[F].routes),
      "/http4s/metrics" -> metricsService[F](metricsRegistry)
    ).orNotFound

  def stream: fs2.Stream[F, ExitCode] =
    BlazeServerBuilder[F]
      .bindHttp(8080)
      .withHttpApp(app)
      .serve
}
