package com.example.http4s.tomcat

import cats.effect._
import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import fs2._
import fs2.StreamApp.ExitCode
import org.http4s.server.HttpMiddleware
import org.http4s.server.metrics._
import org.http4s.server.tomcat.TomcatBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object TomcatExample extends TomcatExampleApp[IO]

class TomcatExampleApp[F[_]: ConcurrentEffect] extends StreamApp[F] {
  val metricsRegistry: MetricRegistry = new MetricRegistry
  val metrics: HttpMiddleware[F] = Metrics[F](metricsRegistry)

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode] = {
    implicit val timer = Timer.derive[F]
    TomcatBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(new ExampleService[F].service), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics/*")
      .mountFilter(NoneShallPass, "/http4s/science/black-knight/*")
      .serve
  }
}
