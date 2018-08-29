package com.example.http4s
package jetty

import cats.effect._
import com.codahale.metrics.MetricRegistry
import fs2._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.HttpMiddleware
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.metrics._

class JettyExample(implicit timer: Timer[IO], ctx: ContextShift[IO]) extends JettyExampleApp[IO] with IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.toList.map(_.head)
}


class JettyExampleApp[F[_]: ConcurrentEffect : Timer : ContextShift] extends Http4sDsl[F] {
  val metricsRegistry: MetricRegistry = new MetricRegistry
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def stream: Stream[F, ExitCode] = {
    JettyBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(new ExampleService[F].service), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics")
      .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
      .serve
  }
}
