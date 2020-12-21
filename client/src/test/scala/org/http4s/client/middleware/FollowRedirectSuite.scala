/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package client
package middleware

import cats.effect._
import cats.effect.std.Semaphore
import cats.syntax.all._
import java.util.concurrent.atomic._
import org.http4s.Uri.uri
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.headers._
import org.typelevel.ci.CIString

class FollowRedirectSuite extends Http4sSuite with Http4sClientDsl[IO] {
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

  test("FollowRedirect should Strip payload headers when switching to GET") {
    // We could test others, and other scenarios, but this was a pain.
    val req = Request[IO](PUT, uri("http://localhost/303")).withEntity("foo")
    client
      .run(req)
      .use { case Ok(resp) =>
        resp.headers.get(CIString("X-Original-Content-Length")).map(_.value).pure[IO]
      }
      .map(_.get)
      .assertEquals("0")
  }

  test("FollowRedirect should Not redirect more than 'maxRedirects' iterations") {
    val statefulApp = HttpRoutes
      .of[IO] { case GET -> Root / "loop" =>
        val body = loopCounter.incrementAndGet.toString
        MovedPermanently(Location(uri("/loop"))).map(_.withEntity(body))
      }
      .orNotFound
    val client = FollowRedirect(3)(Client.fromHttpApp(statefulApp))
    client
      .run(Request[IO](uri = uri("http://localhost/loop")))
      .use {
        case MovedPermanently(resp) => resp.as[String].map(_.toInt)
        case _ => IO.pure(-1)
      }
      .assertEquals(4)
  }

  test("FollowRedirect should Dispose of original response when redirecting") {
    var disposed = 0
    def disposingService(req: Request[IO]) =
      Resource.make(app.run(req))(_ => IO { disposed = disposed + 1 }.void)
    val client = FollowRedirect(3)(Client(disposingService))
    // one for the original, one for the redirect
    client.expect[String](uri("http://localhost/301")).map(_ => disposed).assertEquals(2)
  }

  test("FollowRedirect should Not hang when redirecting") {
    Semaphore[IO](2)
      .flatMap { semaphore =>
        def f(req: Request[IO]) =
          Resource.make(semaphore.tryAcquire.flatMap {
            case true => app.run(req)
            case false => IO.raiseError(new IllegalStateException("Exhausted all connections"))
          })(_ => semaphore.release)
        val client = FollowRedirect(3)(Client(f))
        client.status(Request[IO](uri = uri("http://localhost/loop/3")))
      }
      .assertEquals(Status.Ok)
  }

  test(
    "FollowRedirect should Not send sensitive headers when redirecting to a different authority") {
    val req = PUT(
      "Don't expose mah secrets!",
      uri("http://localhost/different-authority"),
      Header("Authorization", "Bearer s3cr3t"))
    client
      .run(req)
      .use { case Ok(resp) =>
        resp.headers.get(CIString("X-Original-Authorization")).map(_.value).pure[IO]
      }
      .assertEquals(Some(""))
  }

  test("FollowRedirect should Send sensitive headers when redirecting to same authority") {
    val req = PUT(
      "You already know mah secrets!",
      uri("http://localhost/307"),
      Header("Authorization", "Bearer s3cr3t"))
    client
      .run(req)
      .use { case Ok(resp) =>
        resp.headers.get(CIString("X-Original-Authorization")).map(_.value).pure[IO]
      }
      .assertEquals(Some("Bearer s3cr3t"))
  }

  test("FollowRedirect should Record the intermediate URIs") {
    client
      .run(Request[IO](uri = uri("http://localhost/loop/0")))
      .use { case Ok(resp) =>
        IO.pure(FollowRedirect.getRedirectUris(resp))
      }
      .assertEquals(
        List(
          uri("http://localhost/loop/1"),
          uri("http://localhost/loop/2"),
          uri("http://localhost/loop/3")
        ))
  }

  test("FollowRedirect should Not add any URIs when there are no redirects") {
    client
      .run(Request[IO](uri = uri("http://localhost/loop/100")))
      .use { case Ok(resp) =>
        IO.pure(FollowRedirect.getRedirectUris(resp))
      }
      .assertEquals(List.empty[Uri])
  }
}
