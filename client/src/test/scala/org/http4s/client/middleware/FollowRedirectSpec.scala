/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2._
import java.util.concurrent.atomic._
import org.http4s.Uri.uri
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.testing.Http4sLegacyMatchersIO
import org.specs2.mutable.Tables
import org.typelevel.ci.CIString

class FollowRedirectSpec
    extends Http4sSpec
    with Http4sClientDsl[IO]
    with Tables
    with Http4sLegacyMatchersIO {
  private val loopCounter = new AtomicInteger(0)

  val app = HttpRoutes
    .of[IO] {
      case GET -> Root / "loop" / i =>
        val iteration = i.toInt
        if (iteration < 3) {
          val uri = Uri.unsafeFromString(s"/loop/${iteration + 1}")
          MovedPermanently(Location(uri)).map(_.withEntity(iteration.toString))
        } else
          Ok(iteration.toString)

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
    .orNotFound

  val defaultClient = Client.fromHttpApp(app)
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
          case (OPTIONS | PATCH | POST | PUT) if body.nonEmpty =>
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
          .run(req)
          .use {
            case Ok(resp) =>
              val method = resp.headers.get(CIString("X-Original-Method")).fold("")(_.toString)
              val body = resp.as[String]
              body.map(RedirectResponse(method, _))
            case resp =>
              IO.raiseError(UnexpectedStatus(resp.status, req.method, req.uri))
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
        PUT ! MovedPermanently ! "foo" ! false ! Left(
          UnexpectedStatus(MovedPermanently, PUT, Uri.unsafeFromString("foo"))) |
        GET ! Found ! "" ! true ! Right(RedirectResponse("GET", "")) |
        HEAD ! Found ! "" ! true ! Right(RedirectResponse("HEAD", "")) |
        POST ! Found ! "foo" ! true ! Right(RedirectResponse("GET", "")) |
        POST ! Found ! "foo" ! false ! Right(RedirectResponse("GET", "")) |
        PUT ! Found ! "" ! true ! Right(RedirectResponse("PUT", "")) |
        PUT ! Found ! "foo" ! true ! Right(RedirectResponse("PUT", "foo")) |
        PUT ! Found ! "foo" ! false ! Left(
          UnexpectedStatus(Found, PUT, Uri.unsafeFromString("foo"))) |
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
        POST ! TemporaryRedirect ! "foo" ! false ! Left(
          UnexpectedStatus(TemporaryRedirect, POST, Uri.unsafeFromString("foo"))) |
        PUT ! TemporaryRedirect ! "" ! true ! Right(RedirectResponse("PUT", "")) |
        PUT ! TemporaryRedirect ! "foo" ! true ! Right(RedirectResponse("PUT", "foo")) |
        PUT ! TemporaryRedirect ! "foo" ! false ! Left(
          UnexpectedStatus(TemporaryRedirect, PUT, Uri.unsafeFromString("foo"))) |
        GET ! PermanentRedirect ! "" ! true ! Right(RedirectResponse("GET", "")) |
        HEAD ! PermanentRedirect ! "" ! true ! Right(RedirectResponse("HEAD", "")) |
        POST ! PermanentRedirect ! "foo" ! true ! Right(RedirectResponse("POST", "foo")) |
        POST ! PermanentRedirect ! "foo" ! false ! Left(
          UnexpectedStatus(PermanentRedirect, POST, Uri.unsafeFromString("foo"))) |
        PUT ! PermanentRedirect ! "" ! true ! Right(RedirectResponse("PUT", "")) |
        PUT ! PermanentRedirect ! "foo" ! true ! Right(RedirectResponse("PUT", "foo")) |
        PUT ! PermanentRedirect ! "foo" ! false ! Left(
          UnexpectedStatus(PermanentRedirect, PUT, Uri.unsafeFromString("foo"))) | {
          (method, status, body, pure, response) =>
            doIt(method, status, body, pure, response)
        }
    }.pendingUntilFixed

    "Strip payload headers when switching to GET" in {
      // We could test others, and other scenarios, but this was a pain.
      val req = Request[IO](PUT, uri("http://localhost/303")).withEntity("foo")
      client
        .run(req)
        .use { case Ok(resp) =>
          resp.headers.get(CIString("X-Original-Content-Length")).map(_.value).pure[IO]
        }
        .unsafeRunSync()
        .get must be("0")
    }.pendingUntilFixed

    "Not redirect more than 'maxRedirects' iterations" in {
      val statefulApp = HttpRoutes
        .of[IO] { case GET -> Root / "loop" =>
          val body = loopCounter.incrementAndGet.toString
          MovedPermanently(Location(uri("/loop"))).map(_.withEntity(body))
        }
        .orNotFound
      val client = FollowRedirect(3)(Client.fromHttpApp(statefulApp))
      client.run(Request[IO](uri = uri("http://localhost/loop"))).use {
        case MovedPermanently(resp) => resp.as[String].map(_.toInt)
        case _ => IO.pure(-1)
      } must returnValue(4)
    }

    "Dispose of original response when redirecting" in {
      var disposed = 0
      def disposingService(req: Request[IO]) =
        Resource.make(app.run(req))(_ => IO { disposed = disposed + 1 }.void)
      val client = FollowRedirect(3)(Client(disposingService))
      client.expect[String](uri("http://localhost/301")).unsafeRunSync()
      disposed must_== 2 // one for the original, one for the redirect
    }

    "Not hang when redirecting" in {
      Semaphore[IO](2).flatMap { semaphore =>
        def f(req: Request[IO]) =
          Resource.make(semaphore.tryAcquire.flatMap {
            case true => app.run(req)
            case false => IO.raiseError(new IllegalStateException("Exhausted all connections"))
          })(_ => semaphore.release)
        val client = FollowRedirect(3)(Client(f))
        client.status(Request[IO](uri = uri("http://localhost/loop/3")))
      } must returnValue(Status.Ok)
    }

    "Not send sensitive headers when redirecting to a different authority" in {
      val req = PUT(
        "Don't expose mah secrets!",
        uri("http://localhost/different-authority"),
        Header("Authorization", "Bearer s3cr3t"))
      client.run(req).use { case Ok(resp) =>
        resp.headers.get(CIString("X-Original-Authorization")).map(_.value).pure[IO]
      } must returnValue(Some(""))
    }

    "Send sensitive headers when redirecting to same authority" in {
      val req = PUT(
        "You already know mah secrets!",
        uri("http://localhost/307"),
        Header("Authorization", "Bearer s3cr3t"))
      client.run(req).use { case Ok(resp) =>
        resp.headers.get(CIString("X-Original-Authorization")).map(_.value).pure[IO]
      } must returnValue(Some("Bearer s3cr3t"))
    }

    "Record the intermediate URIs" in {
      client.run(Request[IO](uri = uri("http://localhost/loop/0"))).use { case Ok(resp) =>
        IO.pure(FollowRedirect.getRedirectUris(resp))
      } must returnValue(
        List(
          uri("http://localhost/loop/1"),
          uri("http://localhost/loop/2"),
          uri("http://localhost/loop/3")
        ))
    }

    "Not add any URIs when there are no redirects" in {
      client.run(Request[IO](uri = uri("http://localhost/loop/100"))).use { case Ok(resp) =>
        IO.pure(FollowRedirect.getRedirectUris(resp))
      } must returnValue(List.empty[Uri])
    }
  }
}
