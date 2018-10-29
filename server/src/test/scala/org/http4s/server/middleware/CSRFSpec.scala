package org.http4s.server.middleware

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicLong
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Referer
import CSRF.unlift

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

  val dummyRoutes: HttpApp[IO] = HttpRoutes
    .of[IO] {
      case GET -> Root =>
        Ok()
      case POST -> Root =>
        Ok()
    }
    .orNotFound

  val dummyRequest: Request[IO] =
    Request[IO](method = Method.POST).putHeaders(Header("Origin", "http://localhost"))

  val passThroughRequest: Request[IO] = Request[IO]()

  val csrf: CSRF[IO, IO] = CSRF
    .withGeneratedKey[IO, IO](
      clock = testClock,
      headerCheck = CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, None))
    .unsafeRunSync()

  "CSRF" should {
    "pass through and embed a new token for a safe, fresh request if set" in {
      val response = csrf.validate()(dummyRoutes)(passThroughRequest).unsafeRunSync()

      response.status must_== Status.Ok
      response.cookies.exists(_.name == csrf.cookieName) must_== true
    }

    "pass through and not embed a new token for a safe, fresh request" in {
      val csrfNoEmbed: CSRF[IO, IO] = CSRF
        .withGeneratedKey[IO, IO](
          clock = testClock,
          headerCheck = CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, None),
          createIfNotFound = false)
        .unsafeRunSync()

      val response = csrfNoEmbed.validate()(dummyRoutes)(passThroughRequest).unsafeRunSync()

      response.status must_== Status.Ok
      response.cookies.exists(_.name == csrf.cookieName) must_== false
    }

    "pass through and embed a new token on request attribute" in {

      val routes = HttpRoutes.of[IO] {

        case r@GET -> Root =>
          r.attributes.get(CSRF.TokenKey).map(t =>
            Ok().map(_.withAttribute(CSRF.TokenKey(t)))
          ).getOrElse(Forbidden())

      }.orNotFound

      val response = csrf.validate()(routes)(passThroughRequest).unsafeRunSync()

      response.status must_== Status.Ok
      response.attributes.get(CSRF.TokenKey).isDefined must_== true
    }

    "Extract a valid token, with a slightly changed nonce, if present" in {
      val program = for {
        t <- csrf.generateToken[IO]
        req = csrf.embedInRequestCookie(dummyRequest, t)
        newToken <- csrf.refreshedToken[IO](req).valueOrF(IO.raiseError)
      } yield newToken

      val newToken = program.unsafeRunSync()
      //Checks whether it was properly signed
      csrf.extractRaw(unlift(newToken)).isRight must_== true
    }

    "pass a request with valid origin a request without any origin, even with a token" in {
      val program: IO[Response[IO]] = for {
        token <- csrf.generateToken[IO]
        req = csrf.embedInRequestCookie(Request[IO](POST), token)
        v <- csrf.checkCSRFDefault(req, dummyRoutes)
      } yield v

      program.unsafeRunSync().status must_== Status.Forbidden
    }

    "fail a request with an invalid cookie, despite it being a safe method" in {
      val response =
        csrf
          .validate()(dummyRoutes)(
            passThroughRequest.addCookie(RequestCookie(csrf.cookieName, "MOOSE")))
          .unsafeRunSync()

      response.status must_== Status.Forbidden // Must fail
      !response.cookies.exists(_.name == csrf.cookieName) must_== true //Must not embed a new token
    }

    "pass through and embed a slightly different token for a safe request" in {
      val (oldToken, oldRaw, response, newToken, newRaw) =
        (for {
          oldToken <- csrf.generateToken[IO]
          raw1 <- IO.fromEither(csrf.extractRaw(unlift(oldToken)))
          response <- csrf.validate()(dummyRoutes)(
            csrf.embedInRequestCookie(passThroughRequest, oldToken))
          newCookie = response.cookies
            .find(_.name == csrf.cookieName)
            .getOrElse(ResponseCookie("invalid", "Invalid2"))
          raw2 <- IO.fromEither(csrf.extractRaw(newCookie.content))
        } yield (oldToken, raw1, response, newCookie, raw2)).unsafeRunSync()

      response.status must_== Status.Ok
      oldToken must_!= newToken.content
      oldRaw must_== newRaw
    }

    "not validate different tokens" in {
      val program: IO[Boolean] = for {
        t1 <- csrf.generateToken[IO]
        t2 <- csrf.generateToken[IO]
      } yield CSRF.tokensEqual(t1, t2)

      program.unsafeRunSync() must_== false
    }

    "validate for the correct csrf token and origin" in {
      val program: IO[Response[IO]] = for {
        token <- csrf.generateToken[IO]
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest
            .putHeaders(Header(csrf.headerName.value, unlift(token)))
            .addCookie(csrf.cookieName, unlift(token))
        )
      } yield res

      program.unsafeRunSync().status must_== Status.Ok
    }

    "validate for the correct csrf token and put new token on request attributes" in {

      val routes = HttpRoutes.of[IO] {

        case r@POST -> Root =>
          r.attributes.get(CSRF.TokenKey).map(t =>
            Ok().map(_.withAttribute(CSRF.TokenKey(t)))
          ).getOrElse(Forbidden())

      }.orNotFound

      val program: IO[Option[CSRF.CSRFToken]] = for {
        token <- csrf.generateToken[IO]
        res <- csrf.validate()(routes)(
          dummyRequest
            .putHeaders(Header(csrf.headerName.value, unlift(token)))
            .addCookie(csrf.cookieName, unlift(token))
        )
      } yield {
        res.attributes.get(CSRF.TokenKey)
      }

      program.unsafeRunSync().isDefined must_== true
    }

    "validate for the correct csrf token, no origin but with a referrer" in {
      val program: IO[Response[IO]] =
        for {
          token <- csrf.generateToken[IO]
          res <- csrf.validate()(dummyRoutes)(
            csrf.embedInRequestCookie(
              Request[IO](POST)
                .putHeaders(
                  Header(csrf.headerName.value, unlift(token)),
                  Referer(Uri.unsafeFromString("http://localhost/lol"))),
              token)
          )
        } yield res

      program.unsafeRunSync().status must_== Status.Ok
    }

    "fail a request without any origin, even with a token" in {
      val program: IO[Response[IO]] = for {
        token <- csrf.generateToken[IO]
        req = csrf.embedInRequestCookie(Request[IO](POST), token)
        v <- csrf.checkCSRFDefault(req, dummyRoutes)
      } yield v

      program.unsafeRunSync().status must_== Status.Forbidden
    }

    "fail a request with an incorrect origin and incorrect referrer, even with a token" in {
      (for {
        token <- csrf.generateToken[IO]
        res <- csrf.validate()(dummyRoutes)(
          Request[IO](POST)
            .putHeaders(
              Header(csrf.headerName.value, unlift(token)),
              Header("Origin", "http://example.com"),
              Referer(Uri.unsafeFromString("http://example.com/lol")))
            .addCookie(csrf.cookieName, unlift(token))
        )
      } yield res).unsafeRunSync().status must_== Status.Forbidden
    }

    "not validate if token is missing in both" in {
      csrf
        .validate()(dummyRoutes)(dummyRequest)
        .unsafeRunSync()
        .status must_== Status.Forbidden
    }

    "not validate for token missing in header" in {
      (for {
        token <- csrf.generateToken[IO]
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest.addCookie(csrf.cookieName, unlift(token))
        )
      } yield res).unsafeRunSync().status must_== Status.Forbidden
    }

    "not validate for token missing in cookie" in {
      (for {
        token <- csrf.generateToken[IO]
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest.putHeaders(Header(csrf.headerName.value, unlift(token)))
        )
      } yield res).unsafeRunSync().status must_== Status.Forbidden
    }

    "not validate for different tokens" in {
      (for {
        token1 <- csrf.generateToken[IO]
        token2 <- csrf.generateToken[IO]
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest
            .withHeaders(Headers(Header(csrf.headerName.value, unlift(token1))))
            .addCookie(csrf.cookieName, unlift(token2))
        )
      } yield res).unsafeRunSync().status must_== Status.Forbidden
    }

    "not return the same token to mitigate BREACH" in {
      val program: IO[Boolean] =
        for {
          token <- csrf.generateToken[IO]
          raw1 <- IO.fromEither(csrf.extractRaw(unlift(token)))
          res <- csrf.validate()(dummyRoutes)(
            dummyRequest
              .putHeaders(Header(csrf.headerName.value, unlift(token)))
              .addCookie(csrf.cookieName, unlift(token))
          )
          rawContent = res.cookies.find(_.name == csrf.cookieName).map(_.content).getOrElse("")
          raw2 <- IO.fromEither(csrf.extractRaw(rawContent))
        } yield rawContent != token && raw1 == raw2

      program.unsafeRunSync() must_=== true
    }

    "not return a token for a failed CSRF check" in {
      val response = (for {
        token1 <- csrf.generateToken[IO]
        token2 <- csrf.generateToken[IO]
        res <- csrf.validate()(dummyRoutes)(
          dummyRequest
            .putHeaders(Header(csrf.headerName.value, unlift(token1)))
            .addCookie(csrf.cookieName, unlift(token2))
        )
      } yield res).unsafeRunSync()

      response.status must_== Status.Forbidden
      !response.cookies.exists(_.name == csrf.cookieName) must_== true
    }
  }

}
