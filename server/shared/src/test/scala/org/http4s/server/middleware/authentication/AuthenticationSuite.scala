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

package org.http4s.server.middleware.authentication

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.syntax.all._
import org.typelevel.ci._
import scala.concurrent.duration._

class AuthenticationSuite extends Http4sSuite {
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

  test("Failure to authenticate should not run unauthorized routes") {
    val req = Request[IO](uri = uri"/launch-the-nukes")
    var isNuked = false
    val authedValidateNukeService = basicAuthMiddleware(nukeService {
      isNuked = true
    })
    for {
      res <- authedValidateNukeService.orNotFound(req)
      _ <- IO(assert(!isNuked))
    } yield assertEquals(res.status, Unauthorized)
  }

  { // BasicAuthentication
    val basicAuthedService = basicAuthMiddleware(service)

    test("BasicAuthentication should respond to a request without authentication with 401") {
      val req = Request[IO](uri = uri"/")
      basicAuthedService.orNotFound(req).map { res =>
        assertEquals(res.status, Unauthorized)
        assertEquals(
          res.headers.get[`WWW-Authenticate`].map(_.value),
          Some(Challenge("Basic", realm, Map("charset" -> "UTF-8")).toString))
      }
    }

    test("BasicAuthentication should respond to a request with unknown username with 401") {
      val req = Request[IO](
        uri = uri"/",
        headers = Headers(Authorization(BasicCredentials("Wrong User", password))))
      basicAuthedService.orNotFound(req).map { res =>
        assertEquals(res.status, Unauthorized)
        assertEquals(
          res.headers.get[`WWW-Authenticate`].map(_.value),
          Some(Challenge("Basic", realm, Map("charset" -> "UTF-8")).toString))
      }
    }

    test("BasicAuthentication should respond to a request with wrong password with 401") {
      val req = Request[IO](
        uri = uri"/",
        headers = Headers(Authorization(BasicCredentials(username, "Wrong Password"))))
      basicAuthedService.orNotFound(req).map { res =>
        assertEquals(res.status, Unauthorized)
        assertEquals(
          res.headers.get[`WWW-Authenticate`].map(_.value),
          Some(Challenge("Basic", realm, Map("charset" -> "UTF-8")).toString))
      }
    }

    test("BasicAuthentication should respond to a request with correct credentials") {
      val req = Request[IO](
        uri = uri"/",
        headers = Headers(Authorization(BasicCredentials(username, password))))
      basicAuthedService
        .orNotFound(req)
        .map(_.status)
        .assertEquals(Ok)
    }
  }

  private def parse(value: String) =
    `WWW-Authenticate`
      .parse(value)
      .fold(e => sys.error(s"Couldn't parse: '$value', error: ${e.details}'"), identity)

  { // DigestAuthentication
    val digestAuthMiddleware = DigestAuth(realm, authStore)

    test("DigestAuthentication should respond to a request without authentication with 401") {
      val authedService = digestAuthMiddleware(service)
      val req = Request[IO](uri = uri"/")
      authedService.orNotFound(req).map { res =>
        assertEquals(res.status, Unauthorized)
        val opt = res.headers.get[`WWW-Authenticate`].map(_.value)
        assert(clue(opt).isDefined)
        val challenge = parse(opt.get).values.head
        assert(
          challenge match {
            case Challenge("Digest", `realm`, _) => true
            case _ => false
          },
          challenge)
      }
    }

    // Send a request without authorization, receive challenge.
    def doDigestAuth1(digest: HttpApp[IO]): IO[Challenge] = {
      // Get auth data
      val req = Request[IO](uri = uri"/")
      digest(req).map { res =>
        assertEquals(res.status, Unauthorized)
        val opt = res.headers.get[`WWW-Authenticate`].map(_.value)
        assert(clue(opt).isDefined)
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

      DigestUtil
        .computeResponse[IO](method, username, realm, password, uri, nonce, nc, cnonce, qop)
        .flatMap { response =>
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
          val header = Authorization(Credentials.AuthParams(ci"Digest", params))

          val req2 = Request[IO](uri = uri"/", headers = Headers(header))
          digest(req2).flatMap { res2 =>
            if (withReplay) digest(req2).map(res3 => (res2, res3))
            else IO.pure((res2, null))
          }

        }
    }

    test("DigestAuthentication should respond to a request with correct credentials") {
      val digestAuthService = digestAuthMiddleware(service)

      for {
        challenge <- doDigestAuth1(digestAuthService.orNotFound)
        _ <- IO {
          assert(
            challenge match {
              case Challenge("Digest", `realm`, _) => true
              case _ => false
            },
            challenge)
        }
        results <- doDigestAuth2(digestAuthService.orNotFound, challenge, withReplay = true)
        (res2, res3) = results
      } yield {
        assertEquals(res2.status, Ok)

        // Digest prevents replay
        assertEquals(res3.status, Unauthorized)
      }
    }

    test(
      "DigestAuthentication should respond to many concurrent requests while cleaning up nonces") {
      val n = 100
      val digestAuthMiddleware = DigestAuth(realm, authStore, 2.millis, 2.millis)
      val digestAuthService = digestAuthMiddleware(service)
      val results = (1 to n)
        .map(_ =>
          for {
            challenge <- doDigestAuth1(digestAuthService.orNotFound)
            _ <- IO {
              assert(
                (challenge match {
                  case Challenge("Digest", `realm`, _) => true
                  case _ => false
                }),
                challenge)
            }
            res <- doDigestAuth2(digestAuthService.orNotFound, challenge, withReplay = false)
              .map(_._1)
          } yield
          // We don't check whether res.status is Ok since it may not
          // be due to the low nonce stale timer.  Instead, we check
          // that it's found.
          assertNotEquals(res.status, NotFound))
        .toList
      results.parSequence
    }

    test("DigestAuthentication should avoid many concurrent replay attacks") {
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

      results.map { res =>
        assertEquals(res.count(s => s == Ok), 1)
        assertEquals(res.count(s => s == Unauthorized), n - 1)
      }
    }

    test("DigestAuthentication should respond to invalid requests with 401") {
      val digestAuthService = digestAuthMiddleware(service)
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = "abcdef"

      DigestUtil
        .computeResponse[IO](method, username, realm, password, uri, nonce, nc, cnonce, qop)
        .flatMap { response =>
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

          val expected = List.fill(params.size + 1)(Unauthorized)

          val result = (0 to params.size).map { i =>
            val invalidParams = params.toList.take(i) ++ params.toList.drop(i + 1)
            val header = Authorization(
              Credentials.AuthParams(ci"Digest", invalidParams.head, invalidParams.tail: _*))
            val req = Request[IO](uri = uri"/", headers = Headers(header))
            digestAuthService.orNotFound(req).map(_.status)
          }

          result.toList.parSequence.assertEquals(expected)
        }
    }
  }
}
