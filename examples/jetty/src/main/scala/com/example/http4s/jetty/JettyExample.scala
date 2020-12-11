/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.http4s
package jetty

import cats.effect._
import com.codahale.metrics.MetricRegistry
import org.http4s.metrics.dropwizard._
import org.http4s.server.{HttpMiddleware, Server}
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.middleware.Metrics

object JettyExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    JettyExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object JettyExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): JettyBuilder[F] = {
    val metricsRegistry: MetricRegistry = new MetricRegistry
    val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))

    JettyBuilder[F]
      .bindHttp(8080)
      .mountService(metrics(ExampleService[F](blocker).routes), "/http4s")
      .mountService(metricsService(metricsRegistry), "/metrics")
      .mountFilter(NoneShallPass, "/black-knight/*")
  }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      server <- builder[F](blocker).resource
    } yield server
}
