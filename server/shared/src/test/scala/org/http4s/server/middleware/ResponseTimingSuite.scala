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

import cats.effect._
import cats.effect.testkit.TestControl
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.laws.discipline.arbitrary.genFiniteDuration
import org.http4s.syntax.all._
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.ci._

import scala.concurrent.duration._

class ResponseTimingSuite extends Http4sSuite {
  test("add a custom header with timing info") {
    forAllF(genFiniteDuration) { (artificialDelay: FiniteDuration) =>
      val thisService = HttpApp[IO] {
        case GET -> Root / "request" =>
          IO.sleep(artificialDelay) *>
            Ok("request response")
        case _ => NotFound()
      }
      val req = Request[IO](uri = uri"/request")
      val app = ResponseTiming(thisService)
      val res = app(req)

      val header = res
        .map(_.headers.headers.find(_.name == ci"X-Response-Time"))
      TestControl
        .executeEmbed(header.map(_.map(_.value.toInt.milliseconds) === Some(artificialDelay)))
        .assert
    }
  }
}
