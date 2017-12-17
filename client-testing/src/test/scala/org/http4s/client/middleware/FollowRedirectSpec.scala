package org.http4s
package client
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import java.util.concurrent.atomic._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.headers._
import org.specs2.mutable.Tables

class FollowRedirectSpec extends Http4sSpec with Http4sClientDsl[IO] with Tables {

  private val loopCounter = new AtomicInteger(0)

  val service = HttpService[IO] {
    case req @ _ -> Root / "ok" =>
      Ok(
        req.body,
        Header("X-Original-Method", req.method.toString),
        Header(
          "X-Original-Content-Length",
          req.headers.get(`Content-Length`).fold(0L)(_.length).toString),
        Header("X-Original-Authorization", req.headers.get(Authorization.name).fold("")(_.value))
      )
    case _ -> Root / "different-authority" =>
      TemporaryRedirect(Location(uri("http://www.example.com/ok")))
    case _ -> Root / status =>
      Response[IO](status = Status.fromInt(status.toInt).yolo)
        .putHeaders(Location(uri("/ok")))
        .pure[IO]
  }

  val defaultClient = Client.fromHttpService(service)
  val client = FollowRedirect(3)(defaultClient)

  case class RedirectResponse(
      method: String,
      body: String
  )

  "FollowRedirect" should {
    "follow the proper strategy" in {
      def doIt(
          method: Method,
          status: Status,
          body: String,
          pure: Boolean,
          response: Either[Throwable, RedirectResponse]
      ) = {

        val u = uri("http://localhost") / status.code.toString
        val req: Request[IO] = method match {
          case _: Method.PermitsBody if body.nonEmpty =>
            val bodyBytes = body.getBytes.toList
            Request[IO](
              method,
              u,
              body =
                if (pure) Stream.emits(bodyBytes)
                else Stream.emits(bodyBytes))
          case _ =>
            Request(method, u)
        }
        client
          .fetch(req) {
            case Ok(resp) =>
              val method = resp.headers.get("X-Original-Method".ci).fold("")(_.value)
              val body = resp.as[String]
              body.map(RedirectResponse(method, _))
            case resp =>
              IO.raiseError(UnexpectedStatus(resp.status))
          }
          .attempt
          .unsafeRunSync() must beRight(response)
      }

      "method" | "status" | "body" | "pure" | "response" |>
        GET ! MovedPermanently ! "" ! true ! Right(RedirectResponse("GET", "")) |
        HEAD ! MovedPermanently ! "" ! true ! Right(RedirectResponse("HEAD", "")) |
        POST ! MovedPermanently ! "foo" ! true ! Right(RedirectResponse("GET", "")) |
        POST ! MovedPermanently ! "foo" ! false ! Right(RedirectResponse("GET", "")) |
        PUT ! MovedPermanently ! "" ! true ! Right(RedirectResponse("PUT", "")) |
        PUT ! MovedPermanently ! "foo" ! true ! Right(RedirectResponse("PUT", "foo")) |
        PUT ! MovedPermanently ! "foo" ! false ! Left(UnexpectedStatus(MovedPermanently)) |
        GET ! Found ! "" ! true ! Right(RedirectResponse("GET", "")) |
        HEAD ! Found ! "" ! true ! Right(RedirectResponse("HEAD", "")) |
        POST ! Found ! "foo" ! true ! Right(RedirectResponse("GET", "")) |
        POST ! Found ! "foo" ! false ! Right(RedirectResponse("GET", "")) |
        PUT ! Found ! "" ! true ! Right(RedirectResponse("PUT", "")) |
        PUT ! Found ! "foo" ! true ! Right(RedirectResponse("PUT", "foo")) |
        PUT ! Found ! "foo" ! false ! Left(UnexpectedStatus(Found)) |
        GET ! SeeOther ! "" ! true ! Right(RedirectResponse("GET", "")) |
        HEAD ! SeeOther ! "" ! true ! Right(RedirectResponse("HEAD", "")) |
        POST ! SeeOther ! "foo" ! true ! Right(RedirectResponse("GET", "")) |
        POST ! SeeOther ! "foo" ! false ! Right(RedirectResponse("GET", "")) |
        PUT ! SeeOther ! "" ! true ! Right(RedirectResponse("GET", "")) |
        PUT ! SeeOther ! "foo" ! true ! Right(RedirectResponse("GET", "")) |
        PUT ! SeeOther ! "foo" ! false ! Right(RedirectResponse("GET", "")) |
        GET ! TemporaryRedirect ! "" ! true ! Right(RedirectResponse("GET", "")) |
        HEAD ! TemporaryRedirect ! "" ! true ! Right(RedirectResponse("HEAD", "")) |
        POST ! TemporaryRedirect ! "foo" ! true ! Right(RedirectResponse("POST", "foo")) |
        POST ! TemporaryRedirect ! "foo" ! false ! Left(UnexpectedStatus(TemporaryRedirect)) |
        PUT ! TemporaryRedirect ! "" ! true ! Right(RedirectResponse("PUT", "")) |
        PUT ! TemporaryRedirect ! "foo" ! true ! Right(RedirectResponse("PUT", "foo")) |
        PUT ! TemporaryRedirect ! "foo" ! false ! Left(UnexpectedStatus(TemporaryRedirect)) |
        GET ! PermanentRedirect ! "" ! true ! Right(RedirectResponse("GET", "")) |
        HEAD ! PermanentRedirect ! "" ! true ! Right(RedirectResponse("HEAD", "")) |
        POST ! PermanentRedirect ! "foo" ! true ! Right(RedirectResponse("POST", "foo")) |
        POST ! PermanentRedirect ! "foo" ! false ! Left(UnexpectedStatus(PermanentRedirect)) |
        PUT ! PermanentRedirect ! "" ! true ! Right(RedirectResponse("PUT", "")) |
        PUT ! PermanentRedirect ! "foo" ! true ! Right(RedirectResponse("PUT", "foo")) |
        PUT ! PermanentRedirect ! "foo" ! false ! Left(UnexpectedStatus(PermanentRedirect)) | {
        (method, status, body, pure, response) =>
          doIt(method, status, body, pure, response)
      }
    }.pendingUntilFixed

    "Strip payload headers when switching to GET" in {
      // We could test others, and other scenarios, but this was a pain.
      val req = Request[IO](PUT, uri("http://localhost/303")).withBody("foo")
      client
        .fetch(req) {
          case Ok(resp) =>
            resp.headers.get("X-Original-Content-Length".ci).map(_.value).pure[IO]
        }
        .unsafeRunSync()
        .get must be("0")
    }.pendingUntilFixed

    "Not redirect more than 'maxRedirects' iterations" in {
      val statefulService = HttpService[IO] {
        case GET -> Root / "loop" =>
          val body = loopCounter.incrementAndGet.toString
          MovedPermanently(Location(uri("/loop"))).flatMap(_.withBody(body))
      }
      val client = FollowRedirect(3)(Client.fromHttpService(statefulService))
      client.fetch(Request[IO](uri = uri("http://localhost/loop"))) {
        case MovedPermanently(resp) => resp.as[String].map(_.toInt)
        case _ => IO.pure(-1)
      } must returnValue(4)
    }

    "Dispose of original response when redirecting" in {
      var disposed = 0
      val disposingService = service.orNotFound.map { mr =>
        DisposableResponse(mr, IO { disposed = disposed + 1; () })
      }
      val client = FollowRedirect(3)(Client(disposingService, IO.unit))
      client.expect[String](uri("http://localhost/301")).unsafeRunSync()
      disposed must_== 2 // one for the original, one for the redirect
    }

    "Not send sensitive headers when redirecting to a different authority" in {
      val req = PUT(
        uri("http://localhost/different-authority"),
        "Don't expose mah secrets!",
        Header("Authorization", "Bearer s3cr3t"))
      client.fetch(req) {
        case Ok(resp) =>
          resp.headers.get("X-Original-Authorization".ci).map(_.value).pure[IO]
      } must returnValue(Some(""))
    }

    "Send sensitive headers when redirecting to same authority" in {
      val req = PUT(
        uri("http://localhost/307"),
        "You already know mah secrets!",
        Header("Authorization", "Bearer s3cr3t"))
      client.fetch(req) {
        case Ok(resp) =>
          resp.headers.get("X-Original-Authorization".ci).map(_.value).pure[IO]
      } must returnValue(Some("Bearer s3cr3t"))
    }
  }
}
