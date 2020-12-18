/*
 * Copyright 2013 http4s.org
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

package com.example.http4s.blaze

import cats.effect._
import com.codahale.metrics.{Timer => _, _}
import com.example.http4s.ExampleService
import org.http4s.HttpApp
import org.http4s.implicits._
import org.http4s.metrics.dropwizard._
import org.http4s.server.{HttpMiddleware, Router, Server}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Metrics
import scala.concurrent.ExecutionContext.global

class BlazeMetricsExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeMetricsExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object BlazeMetricsExampleApp {
  def httpApp[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): HttpApp[F] = {
    val metricsRegistry: MetricRegistry = new MetricRegistry()
    val metrics: HttpMiddleware[F] = Metrics[F](Dropwizard(metricsRegistry, "server"))
    Router(
      "/http4s" -> metrics(ExampleService[F](blocker).routes),
      "/http4s/metrics" -> metricsService[F](metricsRegistry)
    ).orNotFound
  }

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server[F]] =
    for {
      blocker <- Blocker[F]
      app = httpApp[F](blocker)
      server <- BlazeServerBuilder[F](global)
        .bindHttp(8080)
        .withHttpApp(app)
        .resource
    } yield server
}
