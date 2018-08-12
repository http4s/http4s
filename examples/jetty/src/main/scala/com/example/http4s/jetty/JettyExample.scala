package com.example.http4s
package jetty

import cats.effect._
import com.codahale.metrics.MetricRegistry
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.dsl.Http4sDsl
import org.http4s.server.HttpMiddleware
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.metrics._
import scala.concurrent.ExecutionContext.Implicits.global

object JettyExample extends JettyExampleApp[IO]

class JettyExampleApp[F[_]: ConcurrentEffect] extends StreamApp[F] with Http4sDsl[F] {
  val metricsRegistry: MetricRegistry = new MetricRegistry
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] = {
    implicit val timer = Timer.derive[F]
    JettyBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(new ExampleService[F].service), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics")
      .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
      .serve
  }
}
