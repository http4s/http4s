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
import org.specs2.mutable.Specification
import cats.effect.testing.specs2.CatsIO
import org.http4s.headers.{Connection, Date, `User-Agent`}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import io.chrisdavenport.keypool.Reusable
import scala.concurrent.duration._

class ClientHelpersSpec extends Specification with CatsIO {
  "Request Preprocessing" should {
    "add a date header if not present" in {
      ClientHelpers
        .preprocessRequest(Request[IO](), None)
        .map { req =>
          req.headers.get(Date) must beSome
        }
    }
    "not add a date header if already present" in {
      ClientHelpers
        .preprocessRequest(
          Request[IO](
            headers = Headers.of(Date(HttpDate.Epoch))
          ),
          None)
        .map { req =>
          req.headers.get(Date) must beSome.like { case d: Date =>
            d.date === HttpDate.Epoch
          }
        }
    }
    "add a connection keep-alive header if not present" in {
      ClientHelpers
        .preprocessRequest(Request[IO](), None)
        .map { req =>
          req.headers.get(Connection) must beSome.like { case c: Connection =>
            c.hasKeepAlive must beTrue
          }
        }
    }

    "not add a connection header if already present" in {
      ClientHelpers
        .preprocessRequest(
          Request[IO](headers = Headers.of(Connection(NonEmptyList.of(CIString("close"))))),
          None
        )
        .map { req =>
          req.headers.get(Connection) must beSome.like { case c: Connection =>
            c.hasKeepAlive must beFalse
          }
        }
    }

    "add default user-agent" in {
      ClientHelpers
        .preprocessRequest(Request[IO](), EmberClientBuilder.default[IO].userAgent)
        .map { req =>
          req.headers.get(`User-Agent`) must beSome
        }
    }

    "not change a present user-agent" in {
      val name = "foo"
      ClientHelpers
        .preprocessRequest(
          Request[IO](
            headers = Headers.of(`User-Agent`(ProductId(name, None)))
          ),
          EmberClientBuilder.default[IO].userAgent)
        .map { req =>
          req.headers.get(`User-Agent`) must beSome.like { case e =>
            e.product.value must_=== name
          }
        }
    }
  }

  "Postprocess response" should {
    "reuse when body is run" in {

      for {
        reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

        testResult <-
          ClientHelpers
            .postProcessResponse(
              Request[IO](),
              Response[IO](),
              reuse
            )
            .use { resp =>
              resp.body.compile.drain >>
                reuse.get.map { case r =>
                  r must beEqualTo(Reusable.Reuse)
                }
            }
      } yield testResult
    }

    "do not reuse when body is not run" in {
      for {
        reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

        testResult <-
          ClientHelpers
            .postProcessResponse(
              Request[IO](),
              Response[IO](),
              reuse
            )
            .use { _ =>
              reuse.get.map { case r =>
                r must beEqualTo(Reusable.DontReuse)
              }
            }
      } yield testResult
    }

    "do not reuse when error encountered running stream" in {
      for {
        reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

        testResult <-
          ClientHelpers
            .postProcessResponse(
              Request[IO](),
              Response[IO](body = fs2.Stream.raiseError[IO](new Throwable("Boo!"))),
              reuse
            )
            .use { resp =>
              resp.body.compile.drain.attempt >>
                reuse.get.map { case r =>
                  r must beEqualTo(Reusable.DontReuse)
                }
            }
      } yield testResult
    }

    "do not reuse when cancellation encountered running stream" in {
      for {
        reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

        testResult <-
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
            .use { resp =>
              resp.body.compile.drain.attempt >>
                reuse.get.map { case r =>
                  r must beEqualTo(Reusable.DontReuse)
                }
            }
      } yield testResult
    }.pendingUntilFixed

    "do not reuse when connection close is set on request" in {
      for {
        reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

        testResult <-
          ClientHelpers
            .postProcessResponse(
              Request[IO](headers = Headers.of(Connection(NonEmptyList.of(CIString("close"))))),
              Response[IO](),
              reuse
            )
            .use { resp =>
              resp.body.compile.drain >>
                reuse.get.map { case r =>
                  r must beEqualTo(Reusable.DontReuse)
                }
            }
      } yield testResult
    }

    "do not reuse when connection close is set on response" in {
      for {
        reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

        testResult <-
          ClientHelpers
            .postProcessResponse(
              Request[IO](),
              Response[IO](headers = Headers.of(Connection(NonEmptyList.of(CIString("close"))))),
              reuse
            )
            .use { resp =>
              resp.body.compile.drain >>
                reuse.get.map { case r =>
                  r must beEqualTo(Reusable.DontReuse)
                }
            }
      } yield testResult
    }
  }
}
