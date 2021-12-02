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

import cats.arrow.FunctionK
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Location
import org.http4s.headers.Referer
import org.http4s.server.middleware.CSRF.unlift
import org.http4s.syntax.all._
import org.typelevel.ci._

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

class CSRFSuite extends Http4sSuite {

  /** Create a clock that always ticks forward once per millis() call.
    *
    * This is to emulate scenarios where we want to mitigate BREACH where, in
    * a real world service, a huge number of requests wouldn't be processed
    * before the clock at least traverses a millisecond.
    */
  private val testClock: Clock = new Clock { self =>
    private lazy val clockTick = new AtomicLong(Instant.now().toEpochMilli)

    override def withZone(zone: ZoneId): Clock = this

    def getZone: ZoneId = ZoneId.systemDefault()

    def instant(): Instant =
      Instant.ofEpochMilli(clockTick.incrementAndGet())
  }

  private val cookieName = "csrf-token"
  private val headerName = ci"X-Csrf-Token"

  private val defaultOriginCheck: Request[IO] => Boolean =
    CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, None)

  private val dummyRoutes: HttpApp[IO] = HttpRoutes
    .of[IO] {
      case GET -> Root =>
        Ok()
      case POST -> Root =>
        Ok()
    }
    .orNotFound

  private val dummyRequest: Request[IO] =
    Request[IO](method = Method.POST).putHeaders("Origin" -> "http://localhost")

  private val passThroughRequest: Request[IO] = Request[IO]()

  private val csrfIO: IO[CSRF[IO, IO]] = CSRF
    .withGeneratedKey[IO, IO](defaultOriginCheck)
    .map(_.withClock(testClock).withCookieName(cookieName).build)

  private val csrfFormIO: IO[CSRF[IO, IO]] = CSRF
    .withGeneratedKey[IO, IO](defaultOriginCheck)
    .map(
      _.withClock(testClock)
        .withCookieName(cookieName)
        .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](headerName.toString, FunctionK.id))
        .build
    )

  private val csrfCatchFailureIO: IO[CSRF[IO, IO]] = CSRF
    .withGeneratedKey[IO, IO](defaultOriginCheck)
    .map(
      _.withClock(testClock)
        .withCookieName(cookieName)
        .withOnFailure(
          Response[IO](status = Status.SeeOther, headers = Headers(Location(uri"/")))
            .removeCookie(cookieName)
        )
        .build
    )

  // /

  test("pass through and embed a new token for a safe, fresh request if set") {
    for {
      csrf <- csrfIO
      response <- csrf.validate()(dummyRoutes)(passThroughRequest)
    } yield {
      assertEquals(response.status, Status.Ok)
      assert(response.cookies.exists(_.name == cookieName))
    }
  }

  test("pass through and not embed a new token for a safe, fresh request") {
    for {
      csrfNoEmbed <- CSRF
        .withGeneratedKey[IO, IO](defaultOriginCheck)
        .map(_.withClock(testClock).withCookieName(cookieName).withCreateIfNotFound(false).build)
      response <- csrfNoEmbed.validate()(dummyRoutes)(passThroughRequest)
    } yield {
      assertEquals(response.status, Status.Ok)
      assert(!response.cookies.exists(_.name == cookieName))
    }
  }

  test("extract a valid token, with a slightly changed nonce, if present") {
    for {
      csrf <- csrfIO
      t <- csrf.generateToken[IO]
      req = csrf.embedInRequestCookie(dummyRequest, t)
      newToken <- csrf.refreshedToken[IO](req).valueOrF(IO.raiseError)
      // Checks whether it was properly signed
    } yield assert(csrf.extractRaw(unlift(newToken)).isRight)
  }

  test("extract a valid token from header or form field when form enabled") {
    def check(f: (String, Request[IO]) => Request[IO]): IO[Response[IO]] =
      for {
        csrfForm <- csrfFormIO
        token <- csrfForm.generateToken[IO]
        ts = unlift(token)
        req = csrfForm.embedInRequestCookie(f(ts, dummyRequest), token)
        res <- csrfForm.checkCSRF(req, dummyRoutes.run(req))
      } yield res

    val hn = headerName.toString

    for {
      fromHeader <- check((ts, r) => r.putHeaders(hn -> ts))
      fromForm <- check((ts, r) => r.withEntity(UrlForm(hn -> ts)))
      preferHeader <- check((ts, r) => r.withEntity(UrlForm(hn -> "bogus")).putHeaders(hn -> ts))
    } yield {
      assertEquals(fromHeader.status, Status.Ok)
      assertEquals(fromForm.status, Status.Ok)
      assertEquals(preferHeader.status, Status.Ok)
    }
  }

  test("pass a request with valid origin a request without any origin, even with a token") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      req = csrf.embedInRequestCookie(Request[IO](POST), token)
      v <- csrf.checkCSRF(req, dummyRoutes.run(req))
    } yield assertEquals(v.status, Status.Forbidden)
  }

  test("fail a request with an invalid cookie, despite it being a safe method") {
    for {
      csrf <- csrfIO
      response <- csrf
        .validate()(dummyRoutes)(passThroughRequest.addCookie(RequestCookie(cookieName, "MOOSE")))
    } yield {
      assertEquals(response.status, Status.Forbidden) // Must fail
      assert(!response.cookies.exists(_.name == cookieName)) // Must not embed a new token
    }
  }

  test("pass through and embed a slightly different token for a safe request") {
    for {
      csrf <- csrfIO
      oldToken <- csrf.generateToken[IO]
      oldRaw <- IO.fromEither(csrf.extractRaw(unlift(oldToken)))
      response <-
        csrf.validate()(dummyRoutes)(csrf.embedInRequestCookie(passThroughRequest, oldToken))
      newCookie =
        response.cookies
          .find(_.name == cookieName)
          .getOrElse(ResponseCookie("invalid", "Invalid2"))
      newToken = newCookie.content
      newRaw <- IO.fromEither(csrf.extractRaw(newCookie.content))
    } yield {
      assertEquals(response.status, Status.Ok)
      assertNotEquals(oldToken.toString, newToken)
      assertEquals(oldRaw, newRaw)
    }
  }

  test("not validate different tokens") {
    for {
      csrf <- csrfIO
      t1 <- csrf.generateToken[IO]
      t2 <- csrf.generateToken[IO]
    } yield assert(!CSRF.tokensEqual(t1, t2))
  }

  test("validate for the correct csrf token and origin") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      res <- csrf.validate()(dummyRoutes)(
        dummyRequest
          .putHeaders(headerName.toString -> unlift(token))
          .addCookie(cookieName, unlift(token))
      )
    } yield assertEquals(res.status, Status.Ok)
  }

  test("validate for the correct csrf token, no origin but with a referrer") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      res <- csrf.validate()(dummyRoutes)(
        csrf.embedInRequestCookie(
          Request[IO](POST)
            .putHeaders(headerName.toString -> unlift(token), Referer(uri"http://localhost/lol")),
          token,
        )
      )
    } yield assertEquals(res.status, Status.Ok)
  }

  test("fail a request without any origin, even with a token") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      req = csrf.embedInRequestCookie(Request[IO](POST), token)
      v <- csrf.checkCSRF(req, dummyRoutes.run(req))
    } yield assertEquals(v.status, Status.Forbidden)
  }

  test("fail a request with an incorrect origin and incorrect referrer, even with a token") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      res <- csrf.validate()(dummyRoutes)(
        Request[IO](POST)
          .putHeaders(
            headerName.toString -> unlift(token),
            "Origin" -> "http://example.com",
            Referer(uri"http://example.com/lol"),
          )
          .addCookie(cookieName, unlift(token))
      )
    } yield assertEquals(res.status, Status.Forbidden)
  }

  test("not validate if token is missing in both") {
    csrfIO
      .flatMap(_.validate()(dummyRoutes)(dummyRequest))
      .map(_.status)
      .assertEquals(Status.Forbidden)
  }

  test("not validate for token missing in header") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      res <- csrf.validate()(dummyRoutes)(
        dummyRequest.addCookie(cookieName, unlift(token))
      )
    } yield assertEquals(res.status, Status.Forbidden)
  }

  test("not validate for token missing in cookie") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      res <- csrf.validate()(dummyRoutes)(
        dummyRequest.putHeaders(headerName.toString -> unlift(token))
      )
    } yield assertEquals(res.status, Status.Forbidden)
  }

  test("not validate for different tokens") {
    for {
      csrf <- csrfIO
      token1 <- csrf.generateToken[IO]
      token2 <- csrf.generateToken[IO]
      res <- csrf.validate()(dummyRoutes)(
        dummyRequest
          .withHeaders(headerName.toString -> unlift(token1))
          .addCookie(cookieName, unlift(token2))
      )
    } yield assertEquals(res.status, Status.Forbidden)
  }

  test("not return the same token to mitigate BREACH") {
    for {
      csrf <- csrfIO
      token <- csrf.generateToken[IO]
      raw1 <- IO.fromEither(csrf.extractRaw(unlift(token)))
      res <- csrf.validate()(dummyRoutes)(
        dummyRequest
          .putHeaders(headerName.toString -> unlift(token))
          .addCookie(cookieName, unlift(token))
      )
      rawContent = res.cookies.find(_.name == cookieName).map(_.content).getOrElse("")
      raw2 <- IO.fromEither(csrf.extractRaw(rawContent))
    } yield {
      assertNotEquals(rawContent, token.toString)
      assertEquals(raw1, raw2)
    }
  }

  test("not return a token for a failed CSRF check") {
    for {
      csrf <- csrfIO
      token1 <- csrf.generateToken[IO]
      token2 <- csrf.generateToken[IO]
      res <- csrf.validate()(dummyRoutes)(
        dummyRequest
          .putHeaders(headerName.toString -> unlift(token1))
          .addCookie(cookieName, unlift(token2))
      )
    } yield {
      assertEquals(res.status, Status.Forbidden)
      assert(!res.cookies.exists(_.name == cookieName))
    }
  }

  {
    // "catch a failure if defined explicitly"
    test("pass through and embed a new token for a safe, fresh request if set") {
      for {
        csrfCatchFailure <- csrfCatchFailureIO
        response <- csrfCatchFailure.validate()(dummyRoutes)(passThroughRequest)
      } yield {
        assertEquals(response.status, Status.Ok)
        assert(response.cookies.exists(_.name == cookieName))
      }
    }

    test("fail a request with an invalid cookie, despite it being a safe method") {
      for {
        csrfCatchFailure <- csrfCatchFailureIO
        response <- csrfCatchFailure.validate()(dummyRoutes)(
          passThroughRequest.addCookie(RequestCookie(cookieName, "MOOSE"))
        )
      } yield {
        assertEquals(response.status, Status.SeeOther)
        assert(response.cookies.exists(_.name == cookieName))
      }
    }

    test("pass through and embed a slightly different token for a safe request") {
      for {
        csrfCatchFailure <- csrfCatchFailureIO
        csrf <- csrfIO
        oldToken <- csrfCatchFailure.generateToken[IO]
        oldRaw <- IO.fromEither(csrfCatchFailure.extractRaw(unlift(oldToken)))
        response <- csrfCatchFailure.validate()(dummyRoutes)(
          csrf.embedInRequestCookie(passThroughRequest, oldToken)
        )
        newCookie =
          response.cookies
            .find(_.name == cookieName)
            .getOrElse(ResponseCookie("invalid", "Invalid2"))
        newToken = newCookie.content
        newRaw <- IO.fromEither(csrfCatchFailure.extractRaw(newToken))
      } yield {
        assertEquals(response.status, Status.Ok)
        assertNotEquals(oldToken.toString, newToken)
        assertEquals(oldRaw, newRaw)
      }
    }

    test("fail and catch a request without any origin, even with a token") {
      for {
        csrfCatchFailure <- csrfCatchFailureIO
        token <- csrfCatchFailure.generateToken[IO]
        req = csrfCatchFailure.embedInRequestCookie(Request[IO](POST), token)
        v <- csrfCatchFailure.checkCSRF(req, dummyRoutes.run(req))
      } yield assertEquals(v.status, Status.SeeOther)
    }

    test("not validate if token is missing in both") {
      for {
        csrfCatchFailure <- csrfCatchFailureIO
        response <- csrfCatchFailure.validate()(dummyRoutes)(dummyRequest)
      } yield assertEquals(response.status, Status.SeeOther)
    }

    test("fail a request with an incorrect origin and incorrect referrer, even with a token") {
      for {
        csrfCatchFailure <- csrfCatchFailureIO
        token <- csrfCatchFailure.generateToken[IO]
        res <- csrfCatchFailure.validate()(dummyRoutes)(
          Request[IO](POST)
            .putHeaders(
              headerName.toString -> unlift(token),
              "Origin" -> "http://example.com",
              Referer(uri"http://example.com/lol"),
            )
            .addCookie(cookieName, unlift(token))
        )
      } yield assertEquals(res.status, Status.SeeOther)
    }

    test("return a fresh token for a failed CSRF check") {
      for {
        csrfCatchFailure <- csrfCatchFailureIO
        token1 <- csrfCatchFailure.generateToken[IO]
        token2 <- csrfCatchFailure.generateToken[IO]
        response <- csrfCatchFailure.validate()(dummyRoutes)(
          dummyRequest
            .putHeaders(headerName.toString -> unlift(token1))
            .addCookie(cookieName, unlift(token2))
        )
      } yield {
        assertEquals(response.status, Status.SeeOther)
        assert(response.cookies.exists(_.name == cookieName))
      }
    }
  }
}
