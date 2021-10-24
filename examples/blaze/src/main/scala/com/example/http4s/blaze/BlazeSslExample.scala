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

package com.example.http4s
package blaze

import cats.effect._
import cats.syntax.all._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Server

object BlazeSslExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    BlazeSslExampleApp.resource[IO].use(_ => IO.never).as(ExitCode.Success)
}

object BlazeSslExampleApp {
  def context[F[_]: Sync] =
    ssl.loadContextFromClasspath(ssl.keystorePassword, ssl.keyManagerPassword)

  def builder[F[_]: Async]: F[BlazeServerBuilder[F]] =
    context.map { sslContext =>
      BlazeServerBuilder[F]
        .bindHttp(8443)
        .withSslContext(sslContext)
    }

  def resource[F[_]: Async]: Resource[F, Server] =
    for {
      b <- Resource.eval(builder[F])
      server <- b.withHttpApp(BlazeExampleApp.httpApp).resource
    } yield server
}
