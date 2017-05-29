/** TODO fs2 port
  * Authentication is in flux in master.  It's just going to cause merge
  * conflicts to fix this now.

package org.http4s
package server
package middleware
package authentication

import java.util.concurrent.Executors
import scala.concurrent.duration._

import fs2._
import org.http4s.Status._
import org.http4s.dsl._
import org.http4s.headers._
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{ CaseInsensitiveString, NonEmptyList }

class AuthenticationSpec extends Http4sSpec {

  def nukeService(launchTheNukes: => Unit) = AuthedService[String] {
    case GET -> Root / "launch-the-nukes" as user =>
      for {
        _ <- Task.delay(launchTheNukes)
        r <- Response(Gone).withBody(s"Oops, ${user} launched the nukes.")
      } yield r
  }

  val realm = "Test Realm"
  val username = "Test User"
  val password = "Test Password"

  def authStore(u: String) = Task.now {
    if (u == username) Some(u -> password)
    else None
  }

  def validatePassword(creds: BasicCredentials) = Task.now {
    if (creds.username == username && creds.password == password) Some(creds.username)
    else None
  }

  val service = AuthedService[String] {
    case GET -> Root as user => Ok(user)
    case req as _ => Response.notFound(req)
  }

  "Failure to authenticate" should {
    "not run unauthorized routes" in {
      val req = Request[IO](uri = Uri(path = "/launch-the-nukes"))
      var isNuked = false
      val authedValidateNukeService = BasicAuth(realm, validatePassword _)(nukeService { isNuked = true })
      val res = authedValidateNukeService.orNotFound(req).run
      isNuked must_== false
      res.status must_== (Unauthorized)
    }
  }

  "BasicAuthentication" should {
    val basicAuthedService = BasicAuth(realm, validatePassword _)(service)

    "Respond to a request without authentication with 401" in {
      val req = Request[IO](uri = Uri(path = "/"))
      val res = basicAuthedService.orNotFound(req).run

      res.status must_== (Unauthorized)
      res.headers.get(`WWW-Authenticate`).map(_.value) must_== (Some(Challenge("Basic", realm, Nil.toMap).toString))
    }

    "Respond to a request with unknown username with 401" in {
      val req = Request[IO](uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials("Wrong User", password))))
      val res = basicAuthedService.orNotFound(req).run

      res.status must_== (Unauthorized)
      res.headers.get(`WWW-Authenticate`).map(_.value) must_== (Some(Challenge("Basic", realm, Nil.toMap).toString))
    }

    "Respond to a request with wrong password with 401" in {
      val req = Request[IO](uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials(username, "Wrong Password"))))
      val res = basicAuthedService.orNotFound(req).run

      res.status must_== (Unauthorized)
      res.headers.get(`WWW-Authenticate`).map(_.value) must_== (Some(Challenge("Basic", realm, Nil.toMap).toString))
    }

    "Respond to a request with correct credentials" in {
      val req = Request[IO](uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials(username, password))))
      val res = basicAuthedService.orNotFound(req).run

      res.status must_== (Ok)
    }
  }


  private def parse(value: String) = HttpHeaderParser.WWW_AUTHENTICATE(value).fold(err => sys.error(s"Couldn't parse: $value"), identity)

  "DigestAuthentication" should {
    "Respond to a request without authentication with 401" in {
      val authedService = DigestAuth(realm, authStore)(service)
      val req = Request[IO](uri = Uri(path = "/"))
      val res = authedService.orNotFound(req).run

      res.status must_== (Status.Unauthorized)
      val opt = res.headers.get(`WWW-Authenticate`).map(_.value)
      opt.isDefined must beTrue
      val challenge = parse(opt.get).values.head
      (challenge match {
        case Challenge("Digest", realm, _) => true
        case _ => false
      }) must_== true

      ok
    }

    // Send a request without authorization, receive challenge.
    def doDigestAuth1(digest: HttpService) = {
      // Get auth data
      val req = Request[IO](uri = Uri(path = "/"))
      val res = digest.orNotFound(req).run

      res.status must_== (Unauthorized)
      val opt = res.headers.get(`WWW-Authenticate`).map(_.value)
      opt.isDefined must beTrue
      val challenge = parse(opt.get).values.head
      challenge
    }

    // Respond to a challenge with a correct response.
    // If withReplay is true, also send a replayed request.
    def doDigestAuth2(digest: HttpService, challenge: Challenge, withReplay: Boolean) = {
      // Second request with credentials
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = challenge.params("nonce")

      val response = DigestUtil.computeResponse(method, username, realm, password, uri, nonce, nc, cnonce, qop)
      val params: NonEmptyList[(String, String)] = NonEmptyList(
        "username" -> username, "realm" -> realm, "nonce" -> nonce,
        "uri" -> uri, "qop" -> qop, "nc" -> nc, "cnonce" -> cnonce,
        "response" -> response, "method" -> method
      )
      val header = Authorization(Credentials.AuthParams("Digest".ci, params))

      val req2 = Request[IO](uri = Uri(path = "/"), headers = Headers(header))
      val res2 = digest.orNotFound(req2).run

      if (withReplay) {
        val res3 = digest.orNotFound(req2).run
        (res2, res3)
      } else
        (res2, null)
    }

    "Respond to a request with correct credentials" in {
      val digestAuthService = DigestAuth(realm, authStore)(service)
      val challenge = doDigestAuth1(digestAuthService)

      (challenge match {
        case Challenge("Digest", realm, _) => true
        case _ => false
      }) must_== true

      val (res2, res3) = doDigestAuth2(digestAuthService, challenge, true)

      res2.status must_== (Ok)

      // Digest prevents replay
      res3.status must_== (Unauthorized)

      ok
    }

    "Respond to many concurrent requests while cleaning up nonces" in {
      val n = 100
      val sched = Executors.newFixedThreadPool(4)
      val digestAuthService = DigestAuth(realm, authStore, 2.millis, 2.millis)(service)
      val tasks = (1 to n).map(i =>
        Task {
          val challenge = doDigestAuth1(digestAuthService)
          (challenge match {
            case Challenge("Digest", realm, _) => true
            case _ => false
          }) must_== true
          val res = doDigestAuth2(digestAuthService, challenge, false)._1
          // We don't check whether res.status is Ok since it may not
          // be due to the low nonce stale timer.  Instead, we check
          // that it's found.
          res.status mustNotEqual (NotFound)
        }(sched))
      Task.gatherUnordered(tasks).run

      ok
    }

    "Avoid many concurrent replay attacks" in {
      val n = 100
      val sched = Executors.newFixedThreadPool(4)
      val digestAuthService = DigestAuth(realm, authStore)(service)
      val challenge = doDigestAuth1(digestAuthService)
      val tasks = (1 to n).map(i =>
        Task {
          val res = doDigestAuth2(digestAuthService, challenge, false)._1
          res.status
        }(sched))
      val res = Task.gatherUnordered(tasks).run
      res.filter(s => s == Ok).size must_== 1
      res.filter(s => s == Unauthorized).size must_== n - 1

      ok
    }

    "Respond to invalid requests with 401" in {
      val digestAuthService = DigestAuth(realm, authStore)(service)
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = "abcdef"

      val response = DigestUtil.computeResponse(method, username, realm, password, uri, nonce, nc, cnonce, qop)
      val params = NonEmptyList(
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

      val expected = (0 to params.size).map(i => Unauthorized)

      val result = (0 to params.size).map(i => {
        val invalidParams = params.list.take(i) ++ params.list.drop(i + 1)
        val header = Authorization(Credentials.AuthParams("Digest".ci, invalidParams.head, invalidParams.tail: _*))
        val req = Request(uri = Uri(path = "/"), headers = Headers(header))
        val res = digestAuthService.orNotFound(req).run

        res.status
      })

      expected must_== result

      ok
    }
  }
}
  */
