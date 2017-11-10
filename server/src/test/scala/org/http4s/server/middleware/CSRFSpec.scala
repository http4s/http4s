package org.http4s.server.middleware

import org.http4s._
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process._
import org.http4s.Uri.uri
import org.http4s.dsl._
import org.http4s.headers.{`Set-Cookie` => HCookie}
import org.http4s.internal.compatibility._

class CSRFSpec extends Http4sSpec {

  val dummyService: HttpService = HttpService {
    case GET -> Root =>
      Thread.sleep(1) //Fix so clock doesn't compute the same nonce, to emulate a real service
      Ok()
  }

  val dummyRequest = Request()

  CSRF.withGeneratedKey().map { csrf =>
    "CSRF" should {

      "not validate different tokens" in {
        val equalCheck = for {
          t1 <- csrf.generateToken
          t2 <- csrf.generateToken
        } yield CSRF.isEqual(t1, t2)

        equalCheck.unsafePerformSync must_== false
      }
      "validate for the correct csrf token" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate(dummyService).orNotFound(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token))
              .putHeaders(Header(csrf.headerName, token))
          )
        } yield res).unsafePerformSync

        response.status must_== Status.Ok
      }

      "not validate for token missing in cookie" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate(dummyService).orNotFound(
            dummyRequest
              .putHeaders(Header(csrf.headerName, token))
          )
        } yield res).unsafePerformSync

        response.status must_== Status.Unauthorized
      }

      "not validate for token missing in header" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate(dummyService).orNotFound(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token))
          )
        } yield res).unsafePerformSync

        response.status must_== Status.Unauthorized
      }

      "not validate if token is missing in both" in {
        val response = csrf.validate(dummyService).orNotFound(dummyRequest).unsafePerformSync

        response.status must_== Status.Unauthorized
      }

      "not validate for different tokens" in {
        val response = (for {
          token1 <- csrf.generateToken
          token2 <- csrf.generateToken
          res <- csrf.validate(dummyService).orNotFound(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token1))
              .putHeaders(Header(csrf.headerName, token2))
          )
        } yield res).unsafePerformSync

        response.status must_== Status.Unauthorized
      }

      "not return the same token to mitigate BREACH" in {
        val (response, originalToken) = (for {
          token <- csrf.generateToken
          res <- csrf.validate(dummyService).apply(
            dummyRequest
              .addCookie(Cookie(csrf.cookieName, token))
              .putHeaders(Header(csrf.headerName, token))
          )
          c <- Task.now(
            HCookie
              .from(res.orNotFound.headers)
              .map(_.cookie)
              .find(_.name == csrf.cookieName))
        } yield (c, token)).unsafePerformSync
        response.isDefined must_== true
        response.map(_.content) must_!= Some(originalToken)
      }

      "not return a token for a failed CSRF check" in {
        val response = (for {
          token <- csrf.generateToken
          res <- csrf.validate(dummyService).orNotFound(dummyRequest)
        } yield res).unsafePerformSync

        response.status must_== Status.Unauthorized
        !HCookie
          .from(response.headers)
          .map(_.cookie).exists(_.name == csrf.cookieName) must_== true
      }
    }
  }.unsafePerformSync

}
