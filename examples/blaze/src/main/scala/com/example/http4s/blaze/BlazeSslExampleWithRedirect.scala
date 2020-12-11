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
import fs2._
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext.global

object BlazeSslExampleWithRedirect extends IOApp {
  import BlazeSslExampleWithRedirectApp._

  override def run(args: List[String]): IO[ExitCode] =
    sslStream[IO]
      .mergeHaltBoth(redirectStream[IO])
      .compile
      .drain
      .as(ExitCode.Success)
}

object BlazeSslExampleWithRedirectApp {
  def redirectStream[F[_]: ConcurrentEffect: Timer]: Stream[F, ExitCode] =
    BlazeServerBuilder[F](global)
      .bindHttp(8080)
      .withHttpApp(ssl.redirectApp(8443))
      .serve

  def sslStream[F[_]: ConcurrentEffect: ContextShift: Timer]: Stream[F, ExitCode] =
    Stream.eval(BlazeSslExampleApp.builder[F]).flatMap(_.serve)
}
