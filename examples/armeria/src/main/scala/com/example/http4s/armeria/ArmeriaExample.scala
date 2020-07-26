/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.http4s
package armeria

import cats.effect._
import com.codahale.metrics.MetricRegistry
import com.example.http4s.ExampleService
import org.http4s.metrics.dropwizard.{Dropwizard, _}
import org.http4s.server.armeria.ArmeriaServerBuilder
import org.http4s.server.middleware.Metrics
import org.http4s.server.{HttpMiddleware, Server}

object ArmeriaExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    ArmeriaExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object ArmeriaExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): ArmeriaServerBuilder[F] = {
    val metricsRegistry: MetricRegistry = new MetricRegistry
    val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))

    ArmeriaServerBuilder[F]
      .bindHttp(8080)
      .withHttpRoutes("/http4s", metrics(ExampleService[F](blocker).routes))
      .withHttpRoutes("/metrics", metricsService(metricsRegistry))
      .withDecoratorUnder("/black-knight", new NoneShallPass(_))
  }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      server <- builder[F](blocker).resource
    } yield server
}
