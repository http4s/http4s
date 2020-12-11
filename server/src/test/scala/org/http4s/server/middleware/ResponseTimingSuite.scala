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

import cats.syntax.all._
import cats.effect._
import cats.effect.concurrent.Ref
import org.http4s._
import org.http4s.dsl.io._
import org.typelevel.ci.CIString

import scala.concurrent.duration.TimeUnit

class ResponseTimingSuite extends Http4sSuite {
  import Sys.clock

  private val artificialDelay = 10

  private val thisService = HttpApp[IO] { case GET -> Root / "request" =>
    List.fill(artificialDelay)(Sys.tick()).sequence_ *>
      Ok("request response")
  }

  test("add a custom header with timing info") {
    val req = Request[IO](uri = Uri.uri("/request"))
    val app = ResponseTiming(thisService)
    val res = app(req)

    val header = res
      .map(_.headers.find(_.name == CIString("X-Response-Time")))
    header
      .map(_.forall(_.value.toInt === artificialDelay))
      .assertEquals(true)
  }
}

object Sys {
  private val currentTime: Ref[IO, Long] = Ref.unsafe[IO, Long](System.currentTimeMillis())

  def tick(): IO[Long] = currentTime.modify(l => (l + 1L, l))

  implicit val clock: Clock[IO] = new Clock[IO] {
    override def realTime(unit: TimeUnit): IO[Long] = currentTime.get

    override def monotonic(unit: TimeUnit): IO[Long] = currentTime.get
  }
}
