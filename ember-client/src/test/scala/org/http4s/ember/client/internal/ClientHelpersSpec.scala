/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.client.internal

import cats.syntax.all._
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.concurrent._
import org.http4s._
import org.http4s.implicits._
import org.http4s.headers.{Connection, Date, `User-Agent`}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.AgentProduct
import io.chrisdavenport.keypool.Reusable
import scala.concurrent.duration._

class ClientHelpersSpec extends Http4sSuite {

  test("Request Preprocessing should add a date header if not present") {
    ClientHelpers
      .preprocessRequest(Request[IO](), None)
      .map { req =>
        req.headers.get(Date).isDefined
      }
      .assert
  }
  
  test("Request Preprocessing should not add a date header if already present") {
    ClientHelpers
      .preprocessRequest(
        Request[IO](
          headers = Headers.of(Date(HttpDate.Epoch))
        ),
        None)
      .map { req =>
        req.headers.get(Date).map { case d: Date =>
          d.date
        }
      }
      .assertEquals(Some(HttpDate.Epoch))
  }
  test("Request Preprocessing should add a connection keep-alive header if not present") {
    ClientHelpers
      .preprocessRequest(Request[IO](), None)
      .map { req =>
        req.headers.get(Connection).map { case c: Connection =>
          c.hasKeepAlive
        }
      }
      .assertEquals(Some(true))
  }

  test("Request Preprocessing should not add a connection header if already present") {
    ClientHelpers
      .preprocessRequest(
        Request[IO](headers = Headers.of(Connection(NonEmptyList.of("close".ci)))),
        None
      )
      .map { req =>
        req.headers.get(Connection).map { case c: Connection =>
          c.hasKeepAlive
        }
      }
      .assertEquals(Some(false))
  }

  test("Request Preprocessing should add default user-agent") {
    ClientHelpers
      .preprocessRequest(Request[IO](), EmberClientBuilder.default[IO].userAgent)
      .map { req =>
        req.headers.get(`User-Agent`).isDefined
      }
      .assert
  }

  test("Request Preprocessing should not change a present user-agent") {
    val name = "foo"
    ClientHelpers
      .preprocessRequest(
        Request[IO](
          headers = Headers.of(`User-Agent`(AgentProduct(name, None)))
        ),
        EmberClientBuilder.default[IO].userAgent)
      .map { req =>
        req.headers.get(`User-Agent`).map { case e =>
          e.product.name
        }
      }
      .assertEquals(Some(name))
  }

  test("Postprocess response should reuse when body is run") {

    for {
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      resp =
        ClientHelpers
          .postProcessResponse(
            Request[IO](),
            Response[IO](),
            reuse
          )
      testResult <-
        resp.body.compile.drain >>
          reuse.get.map { case r =>
            assertEquals(r, Reusable.Reuse)
          }
    } yield testResult
  }

  test("Postprocess response should do not reuse when body is not run") {
    for {
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      _ =
        ClientHelpers
          .postProcessResponse(
            Request[IO](),
            Response[IO](),
            reuse
          )

      testResult <-
        reuse.get.map { case r =>
          assertEquals(r, Reusable.DontReuse)
        }
    } yield testResult
  }

  test("Postprocess response should do not reuse when error encountered running stream") {
    for {
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      resp =
        ClientHelpers
          .postProcessResponse(
            Request[IO](),
            Response[IO](body = fs2.Stream.raiseError[IO](new Throwable("Boo!"))),
            reuse
          )
      testResult <-
        resp.body.compile.drain.attempt >>
          reuse.get.map { case r =>
            assertEquals(r, Reusable.DontReuse)
          }
    } yield testResult
  }

  // pending
  test(
    "Postprocess response should do not reuse when cancellation encountered running stream".fail) {
    for {
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      resp =
        ClientHelpers
          .postProcessResponse(
            Request[IO](),
            Response[IO](body = fs2
              .Stream(1, 2, 3, 4, 5)
              .map(_.toByte)
              .zipLeft(
                fs2.Stream.awakeDelay[IO](1.second)
              )
              .interruptAfter(2.seconds)),
            reuse
          )
      testResult <-
        resp.body.compile.drain.attempt >>
          reuse.get.map { case r =>
            assertEquals(r, Reusable.DontReuse)
          }
    } yield testResult
  }

  test("Postprocess response should do not reuse when connection close is set on request") {
    for {
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      resp =
        ClientHelpers
          .postProcessResponse[IO](
            Request[IO](headers = Headers.of(Connection(NonEmptyList.of("close".ci)))),
            Response[IO](),
            reuse
          )
      testResult <-
        resp.body.compile.drain >>
          reuse.get.map { case r =>
            assertEquals(r, Reusable.DontReuse)
          }
    } yield testResult
  }

  test("Postprocess response should do not reuse when connection close is set on response") {
    for {
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      resp =
        ClientHelpers
          .postProcessResponse(
            Request[IO](),
            Response[IO](headers = Headers.of(Connection(NonEmptyList.of("close".ci)))),
            reuse
          )
      testResult <-
        resp.body.compile.drain >>
          reuse.get.map { case r =>
            assertEquals(r, Reusable.DontReuse)
          }
    } yield testResult
  }

}
