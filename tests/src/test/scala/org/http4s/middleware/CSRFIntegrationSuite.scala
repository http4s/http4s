/*
 * Copyright 2013 http4s.org
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

package org.http4s.server.middleware

import java.net.{ServerSocket, URI}
import java.util.concurrent.Executors

import cats._
import cats.arrow._
import cats.data._
import cats.effect._
import cats.syntax.all._
import munit.CatsEffectSuite
import org.http4s.MediaType
import org.http4s._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.multipart._
import org.http4s.server._
import org.http4s.server.blaze._
import org.http4s.server.middleware.CSRF.unlift
import org.http4s.server.middleware._

import scala.concurrent.ExecutionContext.global

class CSRFIntegrationSuite extends Http4sSuite {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  val COOKIE_NAME = "X-Csrf-Token"

  test("CSRF check must work for headers") {
    val port = CSRFIntegrationSuite.findAvailablePort(reuseAddress = true)
    val csrfCheck: Request[IO] => Boolean =
      CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, port.some)
    val csrfBuilder = CSRF[IO, IO](CSRF.generateSigningKey[IO]().unsafeRunSync(), csrfCheck)
    val csrfProtect: CSRF[IO, IO] =
      csrfBuilder
        .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](COOKIE_NAME, FunctionK.id))
        .build
    val token = csrfProtect.generateToken[IO].unsafeRunSync()
    val routes = HttpRoutes.of[IO] { case Method.POST -> Root =>
      Ok("CSRF passed!")
    }
    val service = csrfProtect.validate()(Router("/" -> routes).orNotFound)
    val server = BlazeServerBuilder[IO](CSRFIntegrationSuite.blocker.blockingContext)
      .bindHttp(port, "localhost")
      .withHttpApp(service)
      .resource
    val fiber: Fiber[IO, Nothing] = server.use(_ => IO.never).start.unsafeRunSync()
    val client: Client[IO] = JavaNetClientBuilder[IO](CSRFIntegrationSuite.blocker).create

    val baseReq = Request[IO](
      method = Method.POST,
      headers = Headers.of(Header("Origin", s"http://localhost:$port")),
      uri = Uri(
        scheme = Uri.Scheme.http.some,
        authority = Uri.Authority(host = Uri.RegName("localhost"), port = port.some).some,
        path = "/"
      )
    )
    val req = csrfProtect.embedInRequestCookie(baseReq, token)

    val check = client.expect[String](req).unsafeRunSync()
    fiber.cancel.unsafeRunSync()
    assertEquals(check, "CSRF passed!")
  }

  test("CSRF check must work for url forms") {
    val port = CSRFIntegrationSuite.findAvailablePort(reuseAddress = true)
    val csrfCheck: Request[IO] => Boolean =
      CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, port.some)
    val csrfBuilder = CSRF[IO, IO](CSRF.generateSigningKey[IO]().unsafeRunSync(), csrfCheck)
    val csrfProtect: CSRF[IO, IO] =
      csrfBuilder
        .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](COOKIE_NAME, FunctionK.id))
        .build
    val token = csrfProtect.generateToken[IO].unsafeRunSync()
    val routes = HttpRoutes.of[IO] { case Method.POST -> Root =>
      Ok("CSRF passed!")
    }
    val service = csrfProtect.validate()(Router("/" -> routes).orNotFound)
    val server = BlazeServerBuilder[IO](CSRFIntegrationSuite.blocker.blockingContext)
      .bindHttp(port, "localhost")
      .withHttpApp(service)
      .resource
    val fiber: Fiber[IO, Nothing] = server.use(_ => IO.never).start.unsafeRunSync()
    val client: Client[IO] = JavaNetClientBuilder[IO](CSRFIntegrationSuite.blocker).create

    val baseReq = Request[IO](
      method = Method.POST,
      headers = Headers.of(Header("Origin", s"http://localhost:$port")),
      uri = Uri(
        scheme = Uri.Scheme.http.some,
        authority = Uri.Authority(host = Uri.RegName("localhost"), port = port.some).some,
        path = "/"
      )
    ).withEntity(UrlForm(COOKIE_NAME -> unlift(token)))
    val req = csrfProtect.embedInRequestCookie(baseReq, token)

    val check = client.expect[String](req).unsafeRunSync()
    fiber.cancel.unsafeRunSync()
    assertEquals(check, "CSRF passed!")
  }

  test("CSRF check must work for multipart forms") {
    val port = CSRFIntegrationSuite.findAvailablePort(reuseAddress = true)
    val csrfCheck: Request[IO] => Boolean =
      CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, port.some)
    val csrfBuilder = CSRF[IO, IO](CSRF.generateSigningKey[IO]().unsafeRunSync(), csrfCheck)
    val csrfProtect: CSRF[IO, IO] =
      csrfBuilder
        .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](COOKIE_NAME, FunctionK.id))
        .build
    val token = csrfProtect.generateToken[IO].unsafeRunSync()
    val routes = HttpRoutes.of[IO] { case Method.POST -> Root =>
      Ok("CSRF passed!")
    }
    val service = csrfProtect.validate()(Router("/" -> routes).orNotFound)
    val server = BlazeServerBuilder[IO](CSRFIntegrationSuite.blocker.blockingContext)
      .bindHttp(port, "localhost")
      .withHttpApp(service)
      .resource
    val fiber: Fiber[IO, Nothing] = server.use(_ => IO.never).start.unsafeRunSync()
    val client: Client[IO] = JavaNetClientBuilder[IO](CSRFIntegrationSuite.blocker).create

    val mf = Part.formData[IO](COOKIE_NAME, unlift(token), `Content-Type`(MediaType.text.plain))
    val mp = Multipart(Vector(mf))
    val baseReq = Request(
      method = Method.POST,
      uri = Uri(
        scheme = Uri.Scheme.http.some,
        authority = Uri.Authority(host = Uri.RegName("localhost"), port = port.some).some,
        path = "/"
      ),
      headers = mp.headers
    ).withEntity(mp).putHeaders(Header("Origin", s"http://localhost:$port"))
    val req = csrfProtect.embedInRequestCookie(baseReq, token)

    val check = client.expect[String](req).unsafeRunSync()
    fiber.cancel.unsafeRunSync()
    assertEquals(check, "CSRF passed!")
  }

}

object CSRFIntegrationSuite {
  val blockingPool = Executors.newFixedThreadPool(2)
  val blocker = Blocker.liftExecutorService(blockingPool)

  /** Start a server socket and close it. The port number used by
    * the socket is considered free and returned.
    *
    * @param reuseAddress If set to `false` the returned port will not be useable for some time.
    * @return A port number.
    */
  private def findAvailablePort(reuseAddress: Boolean): Int = {
    val serverSocket = new ServerSocket(0)
    val freePort = serverSocket.getLocalPort
    serverSocket.setReuseAddress(reuseAddress)
    serverSocket.close()
    freePort
  }

}
