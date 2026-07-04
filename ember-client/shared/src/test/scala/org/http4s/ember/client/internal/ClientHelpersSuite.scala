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
import fs2.Stream
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Connection
import org.http4s.headers.Date
import org.http4s.headers.`User-Agent`
import org.typelevel.ci._
import org.typelevel.keypool.Reusable
import org.typelevel.log4cats.noop.NoOpFactory

import scala.concurrent.duration._

class ClientHelpersSuite extends Http4sSuite {
  private[this] val logger = NoOpFactory[IO].getLogger

  private[this] val CloseHeader = Connection(NonEmptyList.of(ci"close"))

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
        Request[IO](headers = Headers(CloseHeader)),
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
          maxDrainBytes = 1024L,
          drainTimeout = 5.seconds,
          logger = logger,
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
        maxDrainBytes = 1024L,
        drainTimeout = 5.seconds,
        logger = logger,
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
          Request[IO](headers = Headers(CloseHeader)),
          Response[IO](),
          IO.pure(Some(Array.emptyByteArray)),
          nextBytes,
          reuse,
          IO.unit,
          maxDrainBytes = 1024L,
          drainTimeout = 5.seconds,
          logger = logger,
        )
      testResult <- reuse.get.map { case r =>
        assertEquals(r, Reusable.DontReuse)
      }
    } yield testResult
  }

  test(
    "Postprocess response should not reuse when connection close is set on request and response body is drained in postprocessing"
  ) {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      body = Stream.emit('.'.toByte).repeat.take(128).covary[IO]
      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](headers = Headers(CloseHeader)),
        Response[IO](entity = Entity.stream(body)),
        IO.pure(None),
        nextBytes,
        reuse,
        IO.unit,
        maxDrainBytes = 1024L,
        drainTimeout = 5.seconds,
        logger = logger,
      )
      testResult <- reuse.get.map(r => assertEquals(r, Reusable.DontReuse))
    } yield testResult
  }

  test("Postprocess response should not reuse when connection close is set on response") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      _ <- ClientHelpers
        .postProcessResponse[IO](
          Request[IO](),
          Response[IO](headers = Headers(CloseHeader)),
          IO.pure(Some(Array.emptyByteArray)),
          nextBytes,
          reuse,
          IO.unit,
          maxDrainBytes = 1024L,
          drainTimeout = 5.seconds,
          logger = logger,
        )
      testResult <- reuse.get.map { case r =>
        assertEquals(r, Reusable.DontReuse)
      }
    } yield testResult
  }

  test(
    "Postprocess response should not reuse when connection close is set on response and response body is drained in postprocessing"
  ) {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      body = Stream.emit('.'.toByte).repeat.take(128).covary[IO]
      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](),
        Response[IO](entity = Entity.stream(body), headers = Headers(CloseHeader)),
        IO.pure(None),
        nextBytes,
        reuse,
        IO.unit,
        maxDrainBytes = 1024L,
        drainTimeout = 5.seconds,
        logger = logger,
      )
      testResult <- reuse.get.map(r => assertEquals(r, Reusable.DontReuse))
    } yield testResult
  }

  test(
    "Postprocess response should reuse when body is partially consumed but within drain limit and timeout"
  ) {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      body = Stream.emit('.'.toByte).repeat.take(128).covary[IO]
      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](),
        Response[IO](entity = Entity.stream(body)),
        IO.pure(None), // body not consumed by user
        nextBytes,
        reuse,
        IO.unit,
        maxDrainBytes = 1024L,
        drainTimeout = 5.seconds,
        logger = logger,
      )
      testResult <- reuse.get.map(r => assertEquals(r, Reusable.Reuse))
    } yield testResult
  }

  test(
    "Postprocess response should reuse when body is partially consumed and exact drain limit can be read within timeout"
  ) {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      body = Stream.emit('.'.toByte).repeat.take(1024).covary[IO]
      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](),
        Response[IO](entity = Entity.stream(body)),
        IO.pure(None), // body not consumed by user
        nextBytes,
        reuse,
        IO.unit,
        maxDrainBytes = 1024L,
        drainTimeout = 5.seconds,
        logger = logger,
      )
      testResult <- reuse.get.map(r => assertEquals(r, Reusable.Reuse))
    } yield testResult
  }

  test("Postprocess response should not reuse when body exceeds drain limit") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      body = Stream.emit('.'.toByte).repeat.take(2048).covary[IO]
      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](),
        Response[IO](entity = Entity.stream(body)),
        IO.pure(None),
        nextBytes,
        reuse,
        IO.unit,
        maxDrainBytes = 1024L,
        drainTimeout = 5.seconds,
        logger = logger,
      )
      testResult <- reuse.get.map(r => assertEquals(r, Reusable.DontReuse))
    } yield testResult
  }

  test("Postprocess response should not reuse when drain times out") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      body = Stream.never[IO]
      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](),
        Response[IO](entity = Entity.stream(body)),
        IO.pure(None),
        nextBytes,
        reuse,
        IO.unit,
        maxDrainBytes = 1024L,
        drainTimeout = 100.milliseconds,
        logger = logger,
      )
      testResult <- reuse.get.map(r => assertEquals(r, Reusable.DontReuse))
    } yield testResult
  }

  test("Postprocess response should not retain drained bytes for next response") {
    for {
      nextBytes <- Ref[IO].of(Array.emptyByteArray)
      reuse <- Ref[IO].of(Reusable.DontReuse: Reusable)
      body = Stream.emit('.'.toByte).repeat.take(128).covary[IO]
      _ <- ClientHelpers.postProcessResponse[IO](
        Request[IO](),
        Response[IO](entity = Entity.stream(body)),
        IO.pure(None),
        nextBytes,
        reuse,
        IO.unit,
        maxDrainBytes = 1024L,
        drainTimeout = 5.seconds,
        logger = logger,
      )
      bytes <- nextBytes.get
    } yield assert(bytes.isEmpty)
  }
}
