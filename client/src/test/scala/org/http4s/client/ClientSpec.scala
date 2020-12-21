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

import scala.concurrent.duration._
import cats.effect.concurrent.Deferred
import cats.effect._
import cats.syntax.all._
import java.io.IOException
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Host
import org.http4s.server.middleware.VirtualHost
import org.http4s.server.middleware.VirtualHost.exact

class ClientSpec extends Http4sSpec with Http4sDsl[IO] {
  val app = HttpApp[IO] { case r =>
    Response[IO](Ok).withEntity(r.body).pure[IO]
  }
  val client: Client[IO] = Client.fromHttpApp(app)

  "mock client" should {
    "read body before dispose" in {
      client.expect[String](Request[IO](POST).withEntity("foo")).unsafeRunSync() must_== "foo"
    }

    "fail to read body after dispose" in {
      Request[IO](POST)
        .withEntity("foo")
        .pure[IO]
        .flatMap { req =>
          // This is bad. Don't do this.
          client.run(req).use(IO.pure).flatMap(_.as[String])
        }
        .attempt
        .unsafeRunSync() must beLeft.like { case e: IOException =>
        e.getMessage == "response was disposed"
      }
    }

    "include a Host header in requests whose URIs are absolute" in {
      val hostClient = Client.fromHttpApp(HttpApp[IO] { r =>
        Ok(r.headers.get(Host).map(_.value).getOrElse("None"))
      })

      hostClient
        .expect[String](Request[IO](GET, Uri.uri("https://http4s.org/")))
        .unsafeRunSync() must_== "http4s.org"
    }

    "include a Host header with a port when the port is non-standard" in {
      val hostClient = Client.fromHttpApp(HttpApp[IO] { case r =>
        Ok(r.headers.get(Host).map(_.value).getOrElse("None"))
      })

      hostClient
        .expect[String](Request[IO](GET, Uri.uri("https://http4s.org:1983/")))
        .unsafeRunSync() must_== "http4s.org:1983"
    }

    "cooperate with the VirtualHost server middleware" in {
      val routes = HttpRoutes.of[IO] { case r =>
        Ok(r.headers.get(Host).map(_.value).getOrElse("None"))
      }

      val hostClient = Client.fromHttpApp(VirtualHost(exact(routes, "http4s.org")).orNotFound)

      hostClient
        .expect[String](Request[IO](GET, Uri.uri("https://http4s.org/")))
        .unsafeRunSync() must_== "http4s.org"
    }

    "allow request to be canceled" in {

      Deferred[IO, Unit]
        .flatMap { cancelSignal =>
          val routes = HttpRoutes.of[IO] { case _ =>
            cancelSignal.complete(()) >> IO.never
          }

          val cancelClient = Client.fromHttpApp(routes.orNotFound)

          Deferred[IO, ExitCase[Throwable]]
            .flatTap { exitCase =>
              cancelClient
                .expect[String](Request[IO](GET, Uri.uri("https://http4s.org/")))
                .guaranteeCase(exitCase.complete)
                .start
                .flatTap(fiber =>
                  cancelSignal.get >> fiber.cancel) // don't cancel until the returned resource is in use
            }
            .flatMap(_.get)
        }
        .unsafeRunTimed(2.seconds) must_== Some(ExitCase.Canceled)

    }
  }
}
