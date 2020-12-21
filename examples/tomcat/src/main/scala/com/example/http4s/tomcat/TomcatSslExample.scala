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

package com.example.http4s.tomcat

import cats.effect._
import com.example.http4s.ssl
import org.http4s.server.Server
import org.http4s.server.tomcat.TomcatBuilder

object TomcatSslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    TomcatSslExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object TomcatSslExampleApp {
  def builder[F[_]: ConcurrentEffect: ContextShift: Timer](blocker: Blocker): TomcatBuilder[F] =
    TomcatExampleApp
      .builder[F](blocker)
      .bindHttp(8443)
      .withSSL(ssl.storeInfo, ssl.keyManagerPassword)

  def resource[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Server] =
    for {
      blocker <- Blocker[F]
      server <- builder[F](blocker).resource
    } yield server
}
