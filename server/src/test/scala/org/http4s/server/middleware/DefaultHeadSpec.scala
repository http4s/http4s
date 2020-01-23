package org.http4s
package server
package middleware

import cats.effect._
import cats.effect.concurrent.Ref
import fs2.Stream
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import cats.effect.testing.specs2.CatsIO

class DefaultHeadSpec extends Http4sSpec with CatsIO {
  override implicit val contextShift: ContextShift[IO] = Http4sSpec.TestContextShift
  override implicit val timer: Timer[IO] = Http4sSpec.TestTimer

  val app = DefaultHead(HttpRoutes.of[IO] {
    case GET -> Root / "hello" =>
      Ok("hello")

    case GET -> Root / "special" =>
      Ok(Header("X-Handled-By", "GET"))

    case HEAD -> Root / "special" =>
      Ok(Header("X-Handled-By", "HEAD"))
  }).orNotFound

  "DefaultHead" should {
    "honor HEAD routes" in {
      val req = Request[IO](Method.HEAD, uri = uri("/special"))
      app(req).map(_.headers.get("X-Handled-By".ci).map(_.value)) must returnValue(Some("HEAD"))
    }

    "return truncated body of corresponding GET on fallthrough" in {
      val req = Request[IO](Method.HEAD, uri = uri("/hello"))
      app(req) must returnBody("")
    }

    "retain all headers of corresponding GET on fallthrough" in {
      val get = Request[IO](Method.GET, uri = uri("/hello"))
      val head = get.withMethod(Method.HEAD)
      app(get).map(_.headers).unsafeRunSync() must_== app(head)
        .map(_.headers)
        .unsafeRunSync()
    }

    "allow GET body to clean up on fallthrough" in {
      for {
        cleanedUpRef <- Ref[IO].of(false)
        route = HttpRoutes.of[IO] {
          case GET -> _ =>
            val body: EntityBody[IO] = Stream.never.onFinalizeWeak(cleanedUpRef.set(true))
            Ok(body)
        }
        app = DefaultHead(route).orNotFound
        resp <- app(Request[IO](Method.HEAD))
        _ <- resp.as[String]
        cleanedUp <- cleanedUpRef.get
      } yield cleanedUp must beTrue
    }
  }
}
