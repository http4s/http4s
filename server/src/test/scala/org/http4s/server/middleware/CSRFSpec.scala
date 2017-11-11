package org.http4s.server.middleware

import cats.data.OptionT
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

class CSRFSpec extends Http4sSpec {
  val dummyService: HttpService[IO] = HttpService[IO] {
    case GET -> Root =>
      Thread.sleep(1) //Necessary to advance the clock
      Ok()
  }

  val dummyRequest: Request[IO] = Request[IO]()
  val orElse: Response[IO] = Response[IO](Status.Forbidden)

  val csrf = CSRF.withGeneratedKey[IO]().unsafeRunSync()
  "CSRF" should {

    "not validate different tokens" in {
      (for {
        t1 <- csrf.generateToken
        t2 <- csrf.generateToken
      } yield CSRF.isEqual(t1, t2)).unsafeRunSync() must_== false
    }

    "validate for the correct csrf token" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate(dummyService)(
          dummyRequest
            .putHeaders(Header(csrf.headerName, token))
            .addCookie(csrf.cookieName, token)
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Ok
    }

    "not validate if token is missing in both" in {
      csrf
        .validate(dummyService)(dummyRequest)
        .getOrElse(orElse)
        .unsafeRunSync()
        .status must_== Status.Forbidden
    }

    "not validate for token missing in header" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate(dummyService)(
          dummyRequest.addCookie(csrf.cookieName, token)
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Forbidden
    }

    "not validate for token missing in cookie" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate(dummyService)(
          dummyRequest.putHeaders(Header(csrf.headerName, token))
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Forbidden
    }

    "not validate for different tokens" in {
      (for {
        token1 <- OptionT.liftF(csrf.generateToken)
        token2 <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate(dummyService)(
          dummyRequest
            .withHeaders(Headers(Header(csrf.headerName, token1)))
            .addCookie(csrf.cookieName, token2)
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Forbidden
    }

    "not return the same token to mitigate BREACH" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate(dummyService)(
          dummyRequest
            .putHeaders(Header(csrf.headerName, token))
            .addCookie(csrf.cookieName, token)
        )
        r <- OptionT.fromOption[IO](res.cookies.find(_.name == csrf.cookieName).map(_.content))
      } yield r == token).value.unsafeRunSync() must beSome(false)
    }

    "not return a token for a failed CSRF check" in {
      val response = (for {
        token1 <- OptionT.liftF(csrf.generateToken)
        token2 <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate(dummyService)(
          dummyRequest
            .putHeaders(Header(csrf.headerName, token1))
            .addCookie(csrf.cookieName, token2)
        )
      } yield res).getOrElse(Response.notFound).unsafeRunSync()

      response.status must_== Status.Forbidden
      !response.cookies.exists(_.name == csrf.cookieName) must_== true
    }
  }

}
