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

package com.example.http4s.blaze.demo.server.endpoints

import cats.effect.{Async, Timer}
import cats.syntax.all._
import java.util.concurrent.TimeUnit
import org.http4s.{ApiVersion => _, _}
import org.http4s.dsl.Http4sDsl
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class TimeoutHttpEndpoint[F[_]](implicit F: Async[F], timer: Timer[F]) extends Http4sDsl[F] {
  val service: HttpRoutes[F] = HttpRoutes.of { case GET -> Root / ApiVersion / "timeout" =>
    val randomDuration = FiniteDuration(Random.nextInt(3) * 1000L, TimeUnit.MILLISECONDS)
    timer.sleep(randomDuration) *> Ok("delayed response")
  }
}
