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

package org.http4s
package server
package middleware
package authentication

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.typelevel.ci.CIString
import scala.concurrent.duration._

class AuthenticationSpec extends Http4sSpec {
  def nukeService(launchTheNukes: => Unit) =
    AuthedRoutes.of[String, IO] { case GET -> Root / "launch-the-nukes" as user =>
      for {
        _ <- IO(launchTheNukes)
        r <- Gone(s"Oops, $user launched the nukes.")
      } yield r
    }

  val realm = "Test Realm"
  val username = "Test User"
  val password = "Test Password"

  def authStore(u: String): IO[Option[(String, String)]] =
    IO.pure {
      if (u === username) Some(u -> password)
      else None
    }

  def validatePassword(creds: BasicCredentials): IO[Option[String]] =
    IO.pure {
      if (creds.username == username && creds.password == password) Some(creds.username)
      else None
    }

  val service = AuthedRoutes.of[String, IO] {
    case GET -> Root as user => Ok(user)
    case req as _ => Response.notFoundFor(req)
  }

  val basicAuthMiddleware = BasicAuth(realm, validatePassword _)

  "Failure to authenticate" should {
    "not run unauthorized routes" in {
      val req = Request[IO](uri = uri"/launch-the-nukes")
      var isNuked = false
      val authedValidateNukeService = basicAuthMiddleware(nukeService {
        isNuked = true
      })
      val res = authedValidateNukeService.orNotFound(req).unsafeRunSync()
      isNuked must_=== false
      res.status must_=== Unauthorized
    }
  }

  "BasicAuthentication" should {
    val basicAuthedService = basicAuthMiddleware(service)

    "Respond to a request without authentication with 401" in {
      val req = Request[IO](uri = uri"/")
      val res = basicAuthedService.orNotFound(req).unsafeRunSync()

      res.status must_=== Unauthorized
      res.headers.get(`WWW-Authenticate`).map(_.value) must beSome(
        Challenge("Basic", realm, Map("charset" -> "UTF-8")).toString)
    }

    "Respond to a request with unknown username with 401" in {
      val req = Request[IO](
        uri = uri"/",
        headers = Headers.of(Authorization(BasicCredentials("Wrong User", password))))
      val res = basicAuthedService.orNotFound(req).unsafeRunSync()

      res.status must_=== Unauthorized
      res.headers.get(`WWW-Authenticate`).map(_.value) must beSome(
        Challenge("Basic", realm, Map("charset" -> "UTF-8")).toString)
    }

    "Respond to a request with wrong password with 401" in {
      val req = Request[IO](
        uri = uri"/",
        headers = Headers.of(Authorization(BasicCredentials(username, "Wrong Password"))))
      val res = basicAuthedService.orNotFound(req).unsafeRunSync()

      res.status must_=== Unauthorized
      res.headers.get(`WWW-Authenticate`).map(_.value) must beSome(
        Challenge("Basic", realm, Map("charset" -> "UTF-8")).toString)
    }

    "Respond to a request with correct credentials" in {
      val req = Request[IO](
        uri = uri"/",
        headers = Headers.of(Authorization(BasicCredentials(username, password))))
      val res = basicAuthedService.orNotFound(req).unsafeRunSync()

      res.status must_=== Ok
    }
  }

  private def parse(value: String) =
    `WWW-Authenticate`
      .parse(value)
      .fold(e => sys.error(s"Couldn't parse: '$value', error: ${e.details}'"), identity)

  "DigestAuthentication" should {
    val digestAuthMiddleware = DigestAuth(realm, authStore)

    "Respond to a request without authentication with 401" in {
      val authedService = digestAuthMiddleware(service)
      val req = Request[IO](uri = uri"/")
      val res = authedService.orNotFound(req).unsafeRunSync()

      res.status must_=== Unauthorized
      val opt = res.headers.get(`WWW-Authenticate`).map(_.value)
      opt must beSome
      val challenge = parse(opt.get).values.head
      (challenge match {
        case Challenge("Digest", `realm`, _) => true
        case _ => false
      }) must beTrue

      ok
    }

    // Send a request without authorization, receive challenge.
    def doDigestAuth1(digest: HttpApp[IO]): IO[Challenge] = {
      // Get auth data
      val req = Request[IO](uri = uri"/")
      digest(req).map { res =>
        res.status must_=== Unauthorized
        val opt = res.headers.get(`WWW-Authenticate`).map(_.value)
        opt must beSome
        parse(opt.get).values.head
      }
    }

    // Respond to a challenge with a correct response.
    // If withReplay is true, also send a replayed request.
    def doDigestAuth2(
        digest: HttpApp[IO],
        challenge: Challenge,
        withReplay: Boolean): IO[(Response[IO], Response[IO])] = {
      // Second request with credentials
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = challenge.params("nonce")

      val response =
        DigestUtil.computeResponse(method, username, realm, password, uri, nonce, nc, cnonce, qop)
      val params: NonEmptyList[(String, String)] = NonEmptyList.of(
        "username" -> username,
        "realm" -> realm,
        "nonce" -> nonce,
        "uri" -> uri,
        "qop" -> qop,
        "nc" -> nc,
        "cnonce" -> cnonce,
        "response" -> response,
        "method" -> method
      )
      val header = Authorization(Credentials.AuthParams(CIString("Digest"), params))

      val req2 = Request[IO](uri = uri"/", headers = Headers.of(header))
      digest(req2).flatMap { res2 =>
        if (withReplay) digest(req2).map(res3 => (res2, res3))
        else IO.pure((res2, null))
      }
    }

    "Respond to a request with correct credentials" in {
      val digestAuthService = digestAuthMiddleware(service)

      val results = for {
        challenge <- doDigestAuth1(digestAuthService.orNotFound)
        _ <- IO {
          (challenge match {
            case Challenge("Digest", `realm`, _) => true
            case _ => false
          }) must beTrue
        }
        results <- doDigestAuth2(digestAuthService.orNotFound, challenge, withReplay = true)
      } yield results

      val (res2, res3) = results.unsafeRunSync()

      res2.status must_=== Ok

      // Digest prevents replay
      res3.status must_=== Unauthorized

      ok
    }

    "Respond to many concurrent requests while cleaning up nonces" in {
      val n = 100
      val digestAuthMiddleware = DigestAuth(realm, authStore, 2.millis, 2.millis)
      val digestAuthService = digestAuthMiddleware(service)
      val results = (1 to n)
        .map(_ =>
          for {
            challenge <- doDigestAuth1(digestAuthService.orNotFound)
            _ <- IO {
              (challenge match {
                case Challenge("Digest", `realm`, _) => true
                case _ => false
              }) must_== true
            }
            res <- doDigestAuth2(digestAuthService.orNotFound, challenge, withReplay = false).map(
              _._1)
          } yield
          // We don't check whether res.status is Ok since it may not
          // be due to the low nonce stale timer.  Instead, we check
          // that it's found.
          res.status mustNotEqual NotFound)
        .toList
      results.parSequence.unsafeRunSync()

      ok
    }

    "Avoid many concurrent replay attacks" in {
      val n = 100
      val digestAuthService = digestAuthMiddleware(service)
      val results = for {
        challenge <- doDigestAuth1(digestAuthService.orNotFound)
        results <- (1 to n)
          .map(_ =>
            doDigestAuth2(digestAuthService.orNotFound, challenge, withReplay = false).map(
              _._1.status))
          .toList
          .parSequence
      } yield results

      val res = results.unsafeRunSync()
      res.count(s => s == Ok) must_=== 1
      res.count(s => s == Unauthorized) must_=== n - 1

      ok
    }

    "Respond to invalid requests with 401" in {
      val digestAuthService = digestAuthMiddleware(service)
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = "abcdef"

      val response =
        DigestUtil.computeResponse(method, username, realm, password, uri, nonce, nc, cnonce, qop)
      val params = NonEmptyList.of(
        "username" -> username,
        "realm" -> realm,
        "nonce" -> nonce,
        "uri" -> uri,
        "qop" -> qop,
        "nc" -> nc,
        "cnonce" -> cnonce,
        "response" -> response,
        "method" -> method
      )

      val expected = (0 to params.size).map(_ => Unauthorized)

      val result = (0 to params.size).map { i =>
        val invalidParams = params.toList.take(i) ++ params.toList.drop(i + 1)
        val header = Authorization(
          Credentials.AuthParams(CIString("Digest"), invalidParams.head, invalidParams.tail: _*))
        val req = Request[IO](uri = uri"/", headers = Headers.of(header))
        val res = digestAuthService.orNotFound(req).unsafeRunSync()
        res.status
      }

      expected must_=== result

      ok
    }
  }
}
