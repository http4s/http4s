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

package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import cats.effect.Ref
import org.http4s._
import org.http4s.dsl.io._
import org.typelevel.ci._
import org.http4s.syntax.all._

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import cats.Applicative

class ResponseTimingSuite extends Http4sSuite {
  import Sys.clock

  private val artificialDelay = 10

  private val thisService = HttpApp[IO] { case GET -> Root / "request" =>
    List.fill(artificialDelay)(Sys.tick()).sequence_ *>
      Ok("request response")
  }

  test("add a custom header with timing info") {
    val req = Request[IO](uri = uri"/request")
    val app = ResponseTiming(thisService)
    val res = app(req)

    val header = res
      .map(_.headers.headers.find(_.name == ci"X-Response-Time"))
    header.map(_.map(_.value.toInt) === Some(artificialDelay)).assert
  }
}

object Sys {
  private val currentTime: Ref[IO, Long] = Ref.unsafe[IO, Long](0L)

  def tick(): IO[Long] = currentTime.modify(l => (l + 1L, l))

  implicit val clock: Clock[IO] = new Clock[IO] {
    override def applicative: Applicative[IO] = Applicative[IO]

    override def realTime: IO[FiniteDuration] =
      currentTime.get.map(millis => FiniteDuration(millis, TimeUnit.MILLISECONDS))

    override def monotonic: IO[FiniteDuration] =
      currentTime.get.map(millis => FiniteDuration(millis, TimeUnit.MILLISECONDS))
  }
}
