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

import cats._
import cats.arrow.FunctionK
import cats.effect._
import cats.effect.kernel.Deferred
import cats.syntax.all._
import fs2.Stream
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Host
import org.http4s.multipart.Multipart
import org.http4s.server.middleware.VirtualHost
import org.http4s.server.middleware.VirtualHost.exact
import org.http4s.syntax.all._
import scodec.bits.ByteVector

class ClientSpec extends Http4sSuite with Http4sDsl[IO] {
  private val app = HttpApp[IO] { case r =>
    Response[IO](Ok).withEntity(r.body).pure[IO]
  }
  val client: Client[IO] = Client.fromHttpApp(app)
  val stream = Stream.range(0, 5).map(_.toString)

  test("mock client should read body before dispose") {
    client.expect[String](Request[IO](POST).withEntity("foo")).assertEquals("foo")
  }

  test("mock client should fail to read body after dispose") {
    Request[IO](POST)
      .withEntity("foo")
      .pure[IO]
      .flatMap { req =>
        // This is bad. Don't do this.
        client.run(req).use(IO.pure).flatMap(_.as[String])
      }
      .attempt
      .map(_.left.toOption.get.getMessage)
      .assertEquals("response was disposed")
  }

  test("mock client should read stream body before dispose") {
    client.expect[String](Request[IO](POST).withEntity(stream)).assertEquals("01234")
  }

  test("mock client should fail to read stream body after dispose") {
    Request[IO](POST)
      .withEntity(stream)
      .pure[IO]
      .flatMap { req =>
        // This is bad. Don't do this.
        client.run(req).use(IO.pure).flatMap(_.as[String])
      }
      .interceptMessage[java.io.IOException]("response was disposed")
  }

  test("mock client with stream request should read strict body") {
    val app = HttpApp[IO] { _ =>
      Response[IO](Ok).withEntity("foo").pure[IO]
    }
    Client.fromHttpApp(app).expect[String](Request[IO](POST).withEntity(stream)).assertEquals("foo")
  }

  test("mock client should include a Host header in requests whose URIs are absolute") {
    val hostClient = Client.fromHttpApp(HttpApp[IO] { r =>
      Ok(r.headers.get[Host].map(_.value).getOrElse("None"))
    })

    hostClient
      .expect[String](Request[IO](GET, uri"https://http4s.org/"))
      .assertEquals("http4s.org")
  }

  test("mock client should include a Host header with a port when the port is non-standard") {
    val hostClient = Client.fromHttpApp(HttpApp[IO] { r =>
      Ok(r.headers.get[Host].map(_.value).getOrElse("None"))
    })

    hostClient
      .expect[String](Request[IO](GET, uri"https://http4s.org:1983/"))
      .assertEquals("http4s.org:1983")
  }

  test("mock client should cooperate with the VirtualHost server middleware") {
    val routes = HttpRoutes.of[IO] { case r =>
      Ok(r.headers.get[Host].map(_.value).getOrElse("None"))
    }

    val hostClient = Client.fromHttpApp(VirtualHost(exact(routes, "http4s.org")).orNotFound)

    hostClient
      .expect[String](Request[IO](GET, uri"https://http4s.org/"))
      .assertEquals("http4s.org")
  }

  test("mock client should allow request to be canceled") {

    Deferred[IO, Unit]
      .flatMap { cancelSignal =>
        val routes = HttpRoutes.of[IO] { case _ =>
          cancelSignal.complete(()) >> IO.never
        }

        val cancelClient = Client.fromHttpApp(routes.orNotFound)

        Deferred[IO, Outcome[IO, Throwable, String]]
          .flatTap { outcome =>
            cancelClient
              .expect[String](Request[IO](GET, uri"https://http4s.org/"))
              .guaranteeCase(oc => outcome.complete(oc).void)
              .start
              .flatTap(fiber =>
                cancelSignal.get >> fiber.cancel
              ) // don't cancel until the returned resource is in use
          }
          .flatMap(_.get)
      }
      .assertEquals(Outcome.canceled[IO, Throwable, String])
  }

  test("translate should be able to catch UnexpectedStatus errors in resource use") {
    case object MyThrowable extends Throwable
    val app = HttpApp[IO] { (_: Request[IO]) =>
      Response(Status.InternalServerError).pure[IO]
    }
    val client = Client.fromHttpApp(app)
    val handleError = new (IO ~> IO) {
      def apply[A](fa: IO[A]): IO[A] = fa.adaptError { case _: UnexpectedStatus =>
        MyThrowable
      }
    }
    client
      .translate[IO](handleError)(FunctionK.id[IO])
      .expect[String](Request[IO]())
      .attempt
      .assertEquals(Left(MyThrowable), "Throwable did not get handled as expected")
  }

  test("translate should be able to catch DecodeFailure errors in resource use") {
    case object MyThrowable extends Throwable
    val app = HttpApp[IO] { (_: Request[IO]) =>
      Response[IO](Status.Ok)
        .withEntity(ByteVector("foo".getBytes))
        .pure[IO]
    }
    val client = Client.fromHttpApp(app)
    val handleError = new (IO ~> IO) {
      def apply[A](fa: IO[A]): IO[A] = fa.adaptError { case _: DecodeFailure =>
        MyThrowable
      }
    }
    client
      .translate[IO](handleError)(FunctionK.id[IO])
      .expect[Multipart[IO]](Request[IO]())
      .attempt
      .assertEquals(Left(MyThrowable), "Throwable did not get handled as expected")
  }
}
