package org.http4s.server.middleware

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicLong

import cats.data.OptionT
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

class CSRFSpec extends Http4sSpec {

  /** Create a clock that always ticks forward once per millis() call.
    *
    * This is to emulate scenarios where we want to mitigate BREACH where, in
    * a real world service, a huge number of requests wouldn't be processed
    * before the clock at least traverses a millisecond.
    */
  val testClock: Clock = new Clock { self =>
    private lazy val clockTick = new AtomicLong(Instant.now().toEpochMilli)

    def withZone(zone: ZoneId): Clock = this

    def getZone: ZoneId = ZoneId.systemDefault()

    def instant(): Instant =
      Instant.ofEpochMilli(clockTick.incrementAndGet())
  }

  val dummyRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root =>
      Ok()
    case POST -> Root =>
      Ok()
  }

  val dummyRequest: Request[IO] = Request[IO](method = Method.POST)
  val passThroughRequest: Request[IO] = Request[IO]()
  val orElse: Response[IO] = Response[IO](Status.NotFound)

  val csrf = CSRF.withGeneratedKey[IO](clock = testClock).unsafeRunSync()
  "CSRF" should {
    "pass through and embed a new token for a safe, fresh request" in {
      val response =
        csrf.validate()(dummyRoutes)(passThroughRequest).getOrElse(orElse).unsafeRunSync()

      response.status must_== Status.Ok
      response.cookies.exists(_.name == csrf.cookieName) must_== true
    }

    "fail a request with an invalid cookie, despite it being a safe method" in {
      val response =
        csrf
          .validate()(dummyRoutes)(
            passThroughRequest.addCookie(RequestCookie(csrf.cookieName, "MOOSE")))
          .getOrElse(orElse)
          .unsafeRunSync()

      response.status must_== Status.Unauthorized // Must fail
      !response.cookies.exists(_.name == csrf.cookieName) must_== true //Must not embed a new token
    }

    "pass through and embed a slightly different token for a safe request" in {
      val (oldToken, oldRaw, response, newToken, newRaw) =
        (for {
          oldToken <- csrf.generateToken
          raw1 <- csrf.extractRaw(oldToken).getOrElse("Invalid1")
          response <- csrf
            .validate()(dummyRoutes)(passThroughRequest.addCookie(csrf.cookieName, oldToken))
            .getOrElse(orElse)
          newCookie <- IO.pure(
            response.cookies
              .find(_.name == csrf.cookieName)
              .getOrElse(ResponseCookie("invalid", "Invalid2")))
          raw2 <- csrf.extractRaw(newCookie.content).getOrElse("Invalid1")
        } yield (oldToken, raw1, response, newCookie, raw2)).unsafeRunSync()

      response.status must_== Status.Ok
      oldToken must_!= newToken.content
      oldRaw must_== newRaw
    }

    "not validate different tokens" in {
      (for {
        t1 <- csrf.generateToken
        t2 <- csrf.generateToken
      } yield CSRF.isEqual(t1, t2)).unsafeRunSync() must_== false
    }

    "validate for the correct csrf token" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest
            .putHeaders(Header(csrf.headerName, token))
            .addCookie(csrf.cookieName, token)
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Ok
    }

    "not validate if token is missing in both" in {
      csrf
        .validate()(dummyRoutes)(dummyRequest)
        .getOrElse(orElse)
        .unsafeRunSync()
        .status must_== Status.Unauthorized
    }

    "not validate for token missing in header" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest.addCookie(csrf.cookieName, token)
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Unauthorized
    }

    "not validate for token missing in cookie" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest.putHeaders(Header(csrf.headerName, token))
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Unauthorized
    }

    "not validate for different tokens" in {
      (for {
        token1 <- OptionT.liftF(csrf.generateToken)
        token2 <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest
            .withHeaders(Headers(Header(csrf.headerName, token1)))
            .addCookie(csrf.cookieName, token2)
        )
      } yield res).getOrElse(orElse).unsafeRunSync().status must_== Status.Unauthorized
    }

    "not return the same token to mitigate BREACH" in {
      (for {
        token <- OptionT.liftF(csrf.generateToken)
        raw1 <- csrf.extractRaw(token)
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest
            .putHeaders(Header(csrf.headerName, token))
            .addCookie(csrf.cookieName, token)
        )
        r <- OptionT.fromOption[IO](res.cookies.find(_.name == csrf.cookieName).map(_.content))
        raw2 <- csrf.extractRaw(r)
      } yield r != token && raw1 == raw2).value.unsafeRunSync() must beSome(true)
    }

    "not return a token for a failed CSRF check" in {
      val response = (for {
        token1 <- OptionT.liftF(csrf.generateToken)
        token2 <- OptionT.liftF(csrf.generateToken)
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest
            .putHeaders(Header(csrf.headerName, token1))
            .addCookie(csrf.cookieName, token2)
        )
      } yield res).getOrElse(Response.notFound).unsafeRunSync()

      response.status must_== Status.Unauthorized
      !response.cookies.exists(_.name == csrf.cookieName) must_== true
    }
  }

}
