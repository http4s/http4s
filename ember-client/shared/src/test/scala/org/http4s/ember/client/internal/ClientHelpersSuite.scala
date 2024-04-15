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

import cats.data.NonEmptyList
import cats.effect._
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Connection
import org.http4s.headers.Date
import org.http4s.headers.`User-Agent`
import org.typelevel.ci._
import org.typelevel.keypool.Reusable

class ClientHelpersSuite extends Http4sSuite {

  test("Request Preprocessing should add a date header if not present") {
    ClientHelpers
      .preprocessRequest(Request[IO](), None)
      .map { req =>
        req.headers.contains[Date]
      }
      .assert
  }

  test("Request Preprocessing should not add a date header if already present") {
    ClientHelpers
      .preprocessRequest(
        Request[IO](
          headers = Headers(Date(HttpDate.Epoch))
        ),
        None,
      )
      .map { req =>
        req.headers.get[Date].map { case d: Date =>
          d.date
        }
      }
      .assertEquals(Some(HttpDate.Epoch))
  }
  test("Request Preprocessing should add a connection keep-alive header if not present") {
    ClientHelpers
      .preprocessRequest(Request[IO](), None)
      .map { req =>
        req.headers.get[Connection].map { case c: Connection =>
          c.hasKeepAlive
        }
      }
      .assertEquals(Some(true))
  }

  test("Request Preprocessing should not add a connection header if already present") {
    ClientHelpers
      .preprocessRequest(
        Request[IO](headers = Headers(Connection(NonEmptyList.of(ci"close")))),
        None,
      )
      .map { req =>
        req.headers.get[Connection].map { case c: Connection =>
          c.hasKeepAlive
        }
      }
      .assertEquals(Some(false))
  }

  test("Request Preprocessing should add default user-agent") {
    ClientHelpers
      .preprocessRequest(Request[IO](), EmberClientBuilder.default[IO].userAgent)
      .map { req =>
        req.headers.contains[`User-Agent`]
      }
      .assert
  }

  test("Request Preprocessing should not change a present user-agent") {
    val name = "foo"
    ClientHelpers
      .preprocessRequest(
        Request[IO](
          headers = Headers(`User-Agent`(ProductId(name, None)))
        ),
        EmberClientBuilder.default[IO].userAgent,
      )
      .map { req =>
        req.headers.get[`User-Agent`].map { case e =>
          e.product.value
        }
      }
      .assertEquals(Some(name))
  }

  test("Postprocess response should reuse") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      _ <- ClientHelpers
        .postProcessResponse[IO](
          Request[IO](),
          Response[IO](),
          IO.pure(Some(Array.emptyByteArray)),
          nextBytes,
          reuse,
          IO.unit,
        )
      testResult <- reuse.get.map { case r =>
        assertEquals(r, Reusable.Reuse)
      }
    } yield testResult
  }

  test("Postprocess response should save drained bytes when reused") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)

      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](),
        Response[IO](),
        IO.pure(Some(Array[Byte](1, 2, 3))),
        nextBytes,
        reuse,
        IO.unit,
      )
      drained <- nextBytes.get
    } yield assertEquals(drained.toList, List[Byte](1, 2, 3))
  }

  test("Postprocess response should not reuse when connection close is set on request") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      _ <- ClientHelpers
        .postProcessResponse[IO](
          Request[IO](headers = Headers(Connection(NonEmptyList.of(ci"close")))),
          Response[IO](),
          IO.pure(Some(Array.emptyByteArray)),
          nextBytes,
          reuse,
          IO.unit,
        )
      testResult <- reuse.get.map { case r =>
        assertEquals(r, Reusable.DontReuse)
      }
    } yield testResult
  }

  test("Postprocess response should do not reuse when connection close is set on response") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      _ <- ClientHelpers
        .postProcessResponse[IO](
          Request[IO](),
          Response[IO](headers = Headers(Connection(NonEmptyList.of(ci"close")))),
          IO.pure(Some(Array.emptyByteArray)),
          nextBytes,
          reuse,
          IO.unit,
        )
      testResult <- reuse.get.map { case r =>
        assertEquals(r, Reusable.DontReuse)
      }
    } yield testResult
  }

  test("Postprocess response should do not reuse when drain is None") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      _ <- ClientHelpers
        .postProcessResponse[IO](
          Request[IO](),
          Response[IO](),
          IO.pure(None),
          nextBytes,
          reuse,
          IO.unit,
        )
      testResult <- reuse.get.map { case r =>
        assertEquals(r, Reusable.DontReuse)
      }
    } yield testResult
  }
}
