package com.example.http4s.tomcat

import cats.effect._
import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import fs2._
import org.http4s.server.HttpMiddleware
import org.http4s.server.metrics._
import org.http4s.server.tomcat.TomcatBuilder

class TomcatExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends TomcatExampleApp[IO] with IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}


class TomcatExampleApp[F[_]: ConcurrentEffect : ContextShift : Timer] {
  val metricsRegistry: MetricRegistry = new MetricRegistry
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def stream: Stream[F, ExitCode] = {
    TomcatBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(new ExampleService[F].service), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics/*")
      .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
      .serve
  }
}
