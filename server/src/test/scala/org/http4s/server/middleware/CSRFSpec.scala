package org.http4s.server.middleware

import cats.implicits._
import fs2.Task
import fs2.interop.cats._
import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import org.http4s.Uri.uri
import org.http4s._
import org.http4s._
import org.http4s.dsl._
import org.http4s.dsl._
import org.http4s.headers.{`Set-Cookie` => HCookie}
import org.http4s.headers.{`Set-Cookie` => HCookie}
import org.http4s.implicits._

class CSRFSpec extends Http4sSpec {

  val testClock: Clock = new Clock { self =>
    private lazy val clockTick = new AtomicLong(Instant.now().toEpochMilli)

    def withZone(zone: ZoneId): Clock = this

    def getZone: ZoneId = ZoneId.systemDefault()

    def instant(): Instant =
      Instant.ofEpochMilli(clockTick.incrementAndGet())
  }

  val dummyService: HttpService = HttpService {
    case POST -> Root =>
      Ok()
    case GET -> Root =>
      Ok()
  }

  val dummyRequest = Request(method = Method.POST)
  val passThrough = Request()

  CSRF.withGeneratedKey(clock = testClock).map { csrf =>
    "CSRF" should {

      "pass through and embed a new token for a safe, fresh request" in {
        val response =
          csrf
            .validate()(dummyService)
            .orNotFound(passThrough).unsafeValue().get

        response.status must_== Status.Ok
        HCookie
          .from(response.headers)
          .map(_.cookie)
          .exists(_.name == csrf.cookieName) must_== true
      }

      "fail a request with an invalid cookie, despite being safe method" in {
        val response =
          csrf
            .validate()(dummyService)
            .orNotFound(
              passThrough.addCookie(Cookie(csrf.cookieName, "MOOSE")))
            .unsafeValue().get

        response.status must_== Status.Unauthorized
        !HCookie
          .from(response.headers)
          .map(_.cookie)
          .exists(_.name == csrf.cookieName) must_== true
      }

      "pass through and embed a slightly different token for a safe request" in {
        val (oldToken, oldRaw, response, newToken, newRaw) = (for {
          token <- csrf.generateToken
          res <- csrf
            .validate()(dummyService)
            .orNotFound(passThrough.addCookie(Cookie(csrf.cookieName, token)))
          tokenRaw1 <- csrf.extractRaw(token).getOrElse("Invalid1")
          newToken <- Task.now(res.cookies.find(_.name == csrf.cookieName).getOrElse(Cookie("invalid", "Invalid2")))
          tokenRaw2 <- csrf.extractRaw(token).getOrElse("Invalid1")
        } yield (token, tokenRaw1, res, newToken, tokenRaw2)).unsafeValue().get

        response.status must_== Status.Ok
        newToken.content must_!= oldToken
        oldRaw must_== newRaw
      }

      "not validate different tokens" in {
        val equalCheck = for {
          t1 <- csrf.generateToken
          t2 <- csrf.generateToken
        } yield CSRF.isEqual(t1, t2)

        equalCheck.unsafeValue() must beSome(false)
      }
      "validate for the correct csrf token" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate()(dummyService).orNotFound(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token))
              .putHeaders(Header(csrf.headerName, token))
          )
        } yield res).unsafeValue().get

        response.status must_== Status.Ok
      }

      "not validate for token missing in cookie" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate()(dummyService).orNotFound(
            dummyRequest
              .putHeaders(Header(csrf.headerName, token))
          )
        } yield res).unsafeValue().get

        response.status must_== Status.Unauthorized
      }

      "not validate for token missing in header" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate()(dummyService).orNotFound(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token))
          )
        } yield res).unsafeValue().get

        response.status must_== Status.Unauthorized
      }

      "not validate if token is missing in both" in {
        val response = csrf.validate()(dummyService).orNotFound(dummyRequest).unsafeValue().get

        response.status must_== Status.Unauthorized
      }

      "not validate for different tokens" in {
        val response = (for {
          token1 <- csrf.generateToken
          token2 <- csrf.generateToken
          res <- csrf.validate()(dummyService).orNotFound(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token1))
              .putHeaders(Header(csrf.headerName, token2))
          )
        } yield res).unsafeValue().get

        response.status must_== Status.Unauthorized
      }

      "not return the same token to mitigate BREACH" in {
        val (response, originalToken, originalRaw, newToken, newRaw) = (for {
          token <- csrf.generateToken
          raw1 <- csrf.extractRaw(token).getOrElse("Invalid1")
          res <- csrf.validate()(dummyService).orNotFound(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token))
              .putHeaders(Header(csrf.headerName, token))
          )
          c <- Task.now(
            HCookie
              .from(res.headers)
              .map(_.cookie)
              .find(_.name == csrf.cookieName).getOrElse(Cookie("", "invalid")))
          raw2 <- csrf.extractRaw(c.content).getOrElse("Invalid2")
        } yield (res, token, raw1, c.content, raw2)).unsafeValue().get
        response.status must_== Status.Ok //Response must have passed through
        originalToken must_!= newToken //Tokens must slightly differ
        originalRaw must_== newRaw //Tokens must have same raw value
      }

      "not return a token for a failed CSRF check" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate()(dummyService).orNotFound(dummyRequest)
        } yield res).unsafeValue().get

        response.status must_== Status.Unauthorized
        !HCookie
          .from(response.headers)
          .map(_.cookie).exists(_.name == csrf.cookieName) must_== true
      }
    }
  }.unsafeValue()
}
