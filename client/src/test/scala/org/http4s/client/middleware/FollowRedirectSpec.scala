package org.http4s
package client
package middleware

import java.util.concurrent.atomic._

import org.http4s.dsl._
import org.http4s.headers._
import org.specs2.mutable.Tables
import scalaz._
import scalaz.concurrent._
import scalaz.stream.Process._
import scalaz.syntax.monad._
import scodec.bits.ByteVector

class FollowRedirectSpec extends Http4sSpec with Tables {

  private val loopCounter = new AtomicInteger(0)

  val service = HttpService {
    case req @ _ -> Root / "ok" =>
      Ok(req.body).putHeaders(
        Header("X-Original-Method", req.method.toString),
        Header("X-Original-Content-Length", req.headers.get(`Content-Length`).fold(0L)(_.length).toString)
      )
    case req @ _ -> Root / status =>
      Response(status = Status.fromInt(status.toInt).yolo)
        .putHeaders(Location(uri("/ok")))
        .pure[Task]
  }

  val defaultClient = MockClient(service)
  val client = FollowRedirect(3)(defaultClient)

  case class RedirectResponse(
    method: String,
    body: String
  )


  "FollowRedirect" should {
    "follow the proper strategy" in {
      def doIt(method: Method, status: Status, body: String, pure: Boolean, response: Throwable \/ RedirectResponse) = {
        val u = uri("http://localhost") / status.code.toString
        val req = method match {
          case _: Method with Method.PermitsBody if body.nonEmpty =>
            val bodyBytes = ByteVector.view(body.getBytes)
            Request(method, u,
              body =
                if (pure) emit(bodyBytes)
                else (emit(bodyBytes) ++ eval_(Task.now(()))))
          case _ =>
            Request(method, u)
        }
        client.fetch(req) {
          case Ok(resp) =>
            val method = resp.headers.get("X-Original-Method".ci).fold("")(_.value)
            val body = resp.as[String]
            body.map(RedirectResponse(method, _))
          case resp =>
            Task.fail(UnexpectedStatus(resp.status))
        }.attemptRun must_== (response)
      }

      "method" | "status"          | "body"    | "pure" | "response"                               |>
      GET      ! MovedPermanently  ! ""        ! true   ! \/-(RedirectResponse("GET", ""))         |
      HEAD     ! MovedPermanently  ! ""        ! true   ! \/-(RedirectResponse("HEAD", ""))        |
      POST     ! MovedPermanently  ! "foo"     ! true   ! \/-(RedirectResponse("GET", ""))         |
      POST     ! MovedPermanently  ! "foo"     ! false  ! \/-(RedirectResponse("GET", ""))         |            
      PUT      ! MovedPermanently  ! "foo"     ! true   ! \/-(RedirectResponse("PUT", "foo"))      |
      PUT      ! MovedPermanently  ! "foo"     ! false  ! -\/(UnexpectedStatus(MovedPermanently))  |
      GET      ! Found             ! ""        ! true   ! \/-(RedirectResponse("GET", ""))         |
      HEAD     ! Found             ! ""        ! true   ! \/-(RedirectResponse("HEAD", ""))        |
      POST     ! Found             ! "foo"     ! true   ! \/-(RedirectResponse("GET", ""))         |
      POST     ! Found             ! "foo"     ! false  ! \/-(RedirectResponse("GET", ""))         |            
      PUT      ! Found             ! "foo"     ! true   ! \/-(RedirectResponse("PUT", "foo"))      |
      PUT      ! Found             ! "foo"     ! false  ! -\/(UnexpectedStatus(Found))             |
      GET      ! SeeOther          ! ""        ! true   ! \/-(RedirectResponse("GET", ""))         |
      HEAD     ! SeeOther          ! ""        ! true   ! \/-(RedirectResponse("HEAD", ""))        |
      POST     ! SeeOther          ! "foo"     ! true   ! \/-(RedirectResponse("GET", ""))         |
      POST     ! SeeOther          ! "foo"     ! false  ! \/-(RedirectResponse("GET", ""))         |            
      PUT      ! SeeOther          ! "foo"     ! true   ! \/-(RedirectResponse("GET", ""))         |
      PUT      ! SeeOther          ! "foo"     ! false  ! \/-(RedirectResponse("GET", ""))         |
      GET      ! TemporaryRedirect ! ""        ! true   ! \/-(RedirectResponse("GET", ""))         |
      HEAD     ! TemporaryRedirect ! ""        ! true   ! \/-(RedirectResponse("HEAD", ""))        |
      POST     ! TemporaryRedirect ! "foo"     ! true   ! \/-(RedirectResponse("POST", "foo"))     |
      POST     ! TemporaryRedirect ! "foo"     ! false  ! -\/(UnexpectedStatus(TemporaryRedirect)) |            
      PUT      ! TemporaryRedirect ! "foo"     ! true   ! \/-(RedirectResponse("PUT", "foo"))      |
      PUT      ! TemporaryRedirect ! "foo"     ! false  ! -\/(UnexpectedStatus(TemporaryRedirect)) |
      GET      ! PermanentRedirect ! ""        ! true   ! \/-(RedirectResponse("GET", ""))         |
      HEAD     ! PermanentRedirect ! ""        ! true   ! \/-(RedirectResponse("HEAD", ""))        |
      POST     ! PermanentRedirect ! "foo"     ! true   ! \/-(RedirectResponse("POST", "foo"))     |
      POST     ! PermanentRedirect ! "foo"     ! false  ! -\/(UnexpectedStatus(PermanentRedirect)) |            
      PUT      ! PermanentRedirect ! "foo"     ! true   ! \/-(RedirectResponse("PUT", "foo"))      |
      PUT      ! PermanentRedirect ! "foo"     ! false  ! -\/(UnexpectedStatus(PermanentRedirect)) |
      { (method, status, body, pure, response) => doIt(method, status, body, pure, response) }
    }

    "Strip payload headers when switching to GET" in {
      // We could test others, and other scenarios, but this was a pain.
      val req = Request(PUT, uri("http://localhost/303")).withBody("foo")
      client.fetch(req) {
        case Ok(resp) =>
          resp.headers.get("X-Original-Content-Length".ci).map(_.value).pure[Task]
      }.attemptRun must be_\/-(Some("0"))
    }

    "Not redirect more than 'maxRedirects' iterations" in {
      val statefulService = HttpService {
        case GET -> Root / "loop" =>
          val body = loopCounter.incrementAndGet.toString
          MovedPermanently(uri("/loop")).withBody(body)
      }
      val client = FollowRedirect(3)(MockClient(statefulService))
      client.fetch(GET(uri("http://localhost/loop"))) {
        case MovedPermanently(resp) => resp.as[String].map(_.toInt)
        case _ => Task.now(-1)
      }.run must_==(4)
    }
  }
}
