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

package com.example.http4s.blaze.demo.server

import cats.effect._
import fs2.Stream
import org.http4s.HttpApp
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import scala.concurrent.ExecutionContext.global

object Server extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    HttpServer.stream[IO].compile.drain.as(ExitCode.Success)
}

object HttpServer {
  def httpApp[F[_]: Sync](ctx: Module[F]): HttpApp[F] =
    Router(
      s"/${endpoints.ApiVersion}/protected" -> ctx.basicAuthHttpEndpoint,
      s"/${endpoints.ApiVersion}" -> ctx.fileHttpEndpoint,
      s"/${endpoints.ApiVersion}/nonstream" -> ctx.nonStreamFileHttpEndpoint,
      "/" -> ctx.httpServices
    ).orNotFound

  def stream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] =
    for {
      blocker <- Stream.resource(Blocker[F])
      client <- BlazeClientBuilder[F](global).stream
      ctx <- Stream(new Module[F](client, blocker))
      exitCode <- BlazeServerBuilder[F](global)
        .bindHttp(8080)
        .withHttpApp(httpApp(ctx))
        .serve
    } yield exitCode
}
