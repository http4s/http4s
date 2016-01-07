package org.http4s
package server
package middleware
package authentication

import java.util.concurrent.Executors

import org.http4s.Status._
import org.http4s.headers._
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.CaseInsensitiveString

import scalaz.concurrent.Task

import scala.concurrent.duration._

class AuthenticationSpec extends Http4sSpec {

  val service = HttpService {
    case r if r.pathInfo == "/" => Response(Ok).withBody("foo")
    case r => Response.notFound(r)
  }

  def nukeService(launchTheNukes: => Unit) = HttpService {
    case r if r.pathInfo == "/launch-the-nukes" => for {
      _ <- Task.delay(launchTheNukes)
      r <- Response(Gone).withBody("oops")
    } yield r
  }

  val realm = "Test Realm"
  val username = "Test User"
  val password = "Test Password"

  def authStore(r: String, u: String) = Task.now {
    if (r == realm && u == username) Some(password)
    else None
  }

  val basic = new BasicAuthentication(realm, authStore)(service)

  "Failure to authenticate" should {
    "not run unauthorized routes" in {
      var isNuked = false
      val basic = new BasicAuthentication(realm, authStore)(nukeService { isNuked = true })
      val req = Request(uri = Uri(path = "/launch-the-nukes"))
      val res = basic.apply(req).run
      isNuked must_== false
      res.status must_== (Unauthorized)
    }
  }

  "BasicAuthentication" should {
    "Respond to a request without authentication with 401" in {
      val req = Request(uri = Uri(path = "/"))
      val res = basic.apply(req).run

      res.status must_== (Unauthorized)
      res.headers.get(`WWW-Authenticate`).map(_.value) must_== (Some(Challenge("Basic", realm, Nil.toMap).toString))
    }

    "Respond to a request with unknown username with 401" in {
      val req = Request(uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials("Wrong User", password))))
      val res = basic.apply(req).run

      res.status must_== (Unauthorized)
      res.headers.get(`WWW-Authenticate`).map(_.value) must_== (Some(Challenge("Basic", realm, Nil.toMap).toString))
    }

    "Respond to a request with wrong password with 401" in {
      val req = Request(uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials(username, "Wrong Password"))))
      val res = basic.apply(req).run

      res.status must_== (Unauthorized)
      res.headers.get(`WWW-Authenticate`).map(_.value) must_== (Some(Challenge("Basic", realm, Nil.toMap).toString))
    }

    "Respond to a request with correct credentials" in {
      val req = Request(uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials(username, password))))
      val res = basic.apply(req).run

      res.status must_== (Ok)
    }
  }


  private def parse(value: String) = HttpHeaderParser.WWW_AUTHENTICATE(value).fold(err => sys.error(s"Couldn't parse: $value"), identity)

  "DigestAuthentication" should {
    "Respond to a request without authentication with 401" in {
      val da = new DigestAuthentication(realm, authStore)
      val digest = da(service)
      val req = Request(uri = Uri(path = "/"))
      val res = digest.apply(req).run

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
      val req = Request(uri = Uri(path = "/"))
      val res = digest.apply(req).run

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
      val params: Map[String, String] = Map("username" -> username, "realm" -> realm, "nonce" -> nonce,
        "uri" -> uri, "qop" -> qop, "nc" -> nc, "cnonce" -> cnonce, "response" -> response,
        "method" -> method)
      val header = Authorization(GenericCredentials(CaseInsensitiveString("Digest"), params))

      val req2 = Request(uri = Uri(path = "/"), headers = Headers(header))
      val res2 = digest.apply(req2).run

      if (withReplay) {
        val res3 = digest.apply(req2).run
        (res2, res3)
      } else
        (res2, null)
    }

    "Respond to a request with correct credentials" in {
      val da = new DigestAuthentication(realm, authStore)
      val digest = da(service)
      val challenge = doDigestAuth1(digest)

      (challenge match {
        case Challenge("Digest", realm, _) => true
        case _ => false
      }) must_== true

      val (res2, res3) = doDigestAuth2(digest, challenge, true)

      res2.status must_== (Ok)

      // Digest prevents replay
      res3.status must_== (Unauthorized)

      ok
    }

    "Respond to many concurrent requests while cleaning up nonces" in {
      val n = 100
      val sched = Executors.newFixedThreadPool(4)
      val da = new DigestAuthentication(realm, authStore, 2.millis, 2.millis)
      val digest = da(service)
      val tasks = (1 to n).map(i =>
        Task {
          val challenge = doDigestAuth1(digest)
          (challenge match {
            case Challenge("Digest", realm, _) => true
            case _ => false
          }) must_== true
          val res = doDigestAuth2(digest, challenge, false)._1
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
      val da = new DigestAuthentication(realm, authStore)
      val digest = da(service)
      val challenge = doDigestAuth1(digest)
      val tasks = (1 to n).map(i =>
        Task {
          val res = doDigestAuth2(digest, challenge, false)._1
          res.status
        }(sched))
      val res = Task.gatherUnordered(tasks).run
      res.filter(s => s == Ok).size must_== 1
      res.filter(s => s == Unauthorized).size must_== n - 1

      ok
    }

    "Respond to invalid requests with 401" in {
      val da = new DigestAuthentication(realm, authStore)
      val digest = da(service)
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = "abcdef"

      val response = DigestUtil.computeResponse(method, username, realm, password, uri, nonce, nc, cnonce, qop)
      val params: Map[String, String] = Map("username" -> username, "realm" -> realm, "nonce" -> nonce,
        "uri" -> uri, "qop" -> qop, "nc" -> nc, "cnonce" -> cnonce, "response" -> response,
        "method" -> method)

      val expected = (0 to params.size).map(i => Unauthorized)

      val result = (0 to params.size).map(i => {
        val invalid_params = params.take(i) ++ params.drop(i + 1)
        val header = Authorization(GenericCredentials(CaseInsensitiveString("Digest"), invalid_params))
        val req = Request(uri = Uri(path = "/"), headers = Headers(header))
        val res = digest.apply(req).run

        res.status
      })

      expected must_== result

      ok
    }
  }
}
