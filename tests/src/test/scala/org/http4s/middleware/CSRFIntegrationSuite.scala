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

import java.net.ServerSocket
import java.nio.charset.StandardCharsets

import cats.arrow._
import cats.effect._
import cats.syntax.all._
import org.http4s.EntityDecoder.multipart
import org.http4s.MediaType
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.client._
import org.http4s.ember.server._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.multipart._
import org.http4s.server._
import org.http4s.server.middleware.CSRF.unlift

import scala.concurrent.ExecutionContext.global

class CSRFIntegrationSuite extends Http4sSuite {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  val COOKIE_NAME = "X-Csrf-Token"

  test("CSRF check must work for headers") {
    val port = CSRFIntegrationSuite.findAvailablePort(reuseAddress = true)
    val routes = HttpRoutes.of[IO] { case Method.POST -> Root =>
      Ok("CSRF passed!")
    }

    val prg =
      for {
        key <- CSRF.generateSigningKey[IO]()
        csrfCheck = CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, port.some)
        csrfBuilder = CSRF[IO, IO](key, csrfCheck)
        csrfProtect =
          csrfBuilder
            .withCookieName(COOKIE_NAME)
            .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](COOKIE_NAME, FunctionK.id))
            .build
        token <- csrfProtect.generateToken[IO]
        service = csrfProtect.validate()(Router("/" -> routes).orNotFound)
        resource = EmberServerBuilder
          .default[IO]
          .withBlocker(testBlocker)
          .withHost("localhost")
          .withPort(port)
          .withHttpApp(service)
          .build
        client <- IO.delay(EmberClientBuilder.default[IO].withBlocker(testBlocker).build)
        baseReq = Request[IO](
          method = Method.POST,
          headers = Headers.of(
            Header("Origin", s"http://localhost:$port"),
            Header(COOKIE_NAME, unlift(token))),
          uri = Uri(
            scheme = Uri.Scheme.http.some,
            authority = Uri.Authority(host = Uri.RegName("localhost"), port = port.some).some,
            path = "/"
          )
        )
        req = csrfProtect.embedInRequestCookie(baseReq, token)
        fiber <- resource
          .use(server =>
            IO.delay(println(s"Server started at ${server.address}!")) >> client.use(
              _.expect[String](req)))
          .start
        check <- fiber.join
      } yield check

    prg.assertEquals("CSRF passed!")
  }

  test("CSRF check must work for url forms") {
    val port = CSRFIntegrationSuite.findAvailablePort(reuseAddress = true)
    val routes = HttpRoutes.of[IO] { case req @ Method.POST -> Root =>
      req.decode[UrlForm] { _ =>
        Ok("CSRF passed!")
      }
    }

    val prg =
      for {
        key <- CSRF.generateSigningKey[IO]()
        csrfCheck = CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, port.some)
        csrfBuilder = CSRF[IO, IO](key, csrfCheck)
        csrfProtect =
          csrfBuilder
            .withCookieName(COOKIE_NAME)
            .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](COOKIE_NAME, FunctionK.id))
            .build
        token <- csrfProtect.generateToken[IO]
        service = csrfProtect.validate()(Router("/" -> routes).orNotFound)
        resource = EmberServerBuilder
          .default[IO]
          .withBlocker(testBlocker)
          .withHost("localhost")
          .withPort(port)
          .withHttpApp(service)
          .build
        client <- IO.delay(EmberClientBuilder.default[IO].withBlocker(testBlocker).build)
        baseReq = Request[IO](
          method = Method.POST,
          headers = Headers.of(Header("Origin", s"http://localhost:$port")),
          uri = Uri(
            scheme = Uri.Scheme.http.some,
            authority = Uri.Authority(host = Uri.RegName("localhost"), port = port.some).some,
            path = "/"
          )
        ).withEntity(UrlForm(COOKIE_NAME -> unlift(token)))
        req = csrfProtect.embedInRequestCookie(baseReq, token)
        fiber <- resource
          .use(server =>
            IO.delay(println(s"Server started at ${server.address}!")) >> client.use(
              _.expect[String](req)))
          .start
        check <- fiber.join
      } yield check

    prg.assertEquals("CSRF passed!")
  }

  test("CSRF check must work for multipart forms") {
    val port = CSRFIntegrationSuite.findAvailablePort(reuseAddress = true)
    val multipartRoutes = HttpRoutes.of[IO] { case req @ Method.POST -> Root =>
      req.decodeWith(multipart[IO], strict = true) { _ =>
        Ok("CSRF passed!")
      }
    }

    val prg =
      for {
        key <- CSRF.generateSigningKey[IO]()
        csrfCheck = CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, port.some)
        csrfBuilder = CSRF[IO, IO](key, csrfCheck)
        csrfProtect =
          csrfBuilder
            .withCookieName(COOKIE_NAME)
            .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](COOKIE_NAME, FunctionK.id))
            .build
        token <- csrfProtect.generateToken[IO]
        service = csrfProtect.validate()(Router("/" -> multipartRoutes).orNotFound)
        resource = EmberServerBuilder
          .default[IO]
          .withBlocker(testBlocker)
          .withHost("localhost")
          .withPort(port)
          .withHttpApp(service)
          .build
        client <- IO.delay(EmberClientBuilder.default[IO].withBlocker(testBlocker).build)
        mf = Part.formData[IO](COOKIE_NAME, unlift(token), `Content-Type`(MediaType.text.plain))
        mp = Multipart(Vector(mf))
        baseReq = Request[IO](
          method = Method.POST,
          uri = Uri(
            scheme = Uri.Scheme.http.some,
            authority = Uri.Authority(host = Uri.RegName("localhost"), port = port.some).some,
            path = "/"
          ),
          headers = mp.headers
        ).withEntity(mp).putHeaders(Header("Origin", s"http://localhost:$port"))
        req = csrfProtect.embedInRequestCookie(baseReq, token)
        fiber <- resource
          .use(server =>
            IO.delay(println(s"Server started at ${server.address}!")) >> client.use(
              _.expect[String](req)))
          .start
        check <- fiber.join
      } yield check

    prg.assertEquals("CSRF passed!")
  }

  test("CSRF check must work for multipart forms with files") {
    val port = CSRFIntegrationSuite.findAvailablePort(reuseAddress = true)
    val multipartRoutes = HttpRoutes.of[IO] { case req @ Method.POST -> Root =>
      req.decodeWith(multipart[IO], strict = true) { _ =>
        Ok("CSRF passed!")
      }
    }

    val prg =
      for {
        key <- CSRF.generateSigningKey[IO]()
        csrfCheck = CSRF.defaultOriginCheck[IO](_, "localhost", Uri.Scheme.http, port.some)
        csrfBuilder = CSRF[IO, IO](key, csrfCheck)
        csrfProtect =
          csrfBuilder
            .withCookieName(COOKIE_NAME)
            .withCSRFCheck(CSRF.checkCSRFinHeaderAndForm[IO, IO](COOKIE_NAME, FunctionK.id))
            .build
        token <- csrfProtect.generateToken[IO]
        service = csrfProtect.validate()(Router("/" -> multipartRoutes).orNotFound)
        resource = EmberServerBuilder
          .default[IO]
          .withBlocker(testBlocker)
          .withHost("localhost")
          .withPort(port)
          .withHttpApp(service)
          .build
        client <- IO.delay(EmberClientBuilder.default[IO].withBlocker(testBlocker).build)
        mf1 = Part.formData[IO](COOKIE_NAME, unlift(token), `Content-Type`(MediaType.text.plain))
        file: EntityBody[IO] = fs2.Stream.emits(
          scala.util.Random.alphanumeric.take(2048).mkString.getBytes(StandardCharsets.UTF_8))
        mf2 = Part.fileData("file", "passport.txt", file)
        mp = Multipart(Vector(mf1, mf2))
        baseReq = Request[IO](
          method = Method.POST,
          uri = Uri(
            scheme = Uri.Scheme.http.some,
            authority = Uri.Authority(host = Uri.RegName("localhost"), port = port.some).some,
            path = "/"
          ),
          headers = mp.headers
        ).withEntity(mp).putHeaders(Header("Origin", s"http://localhost:$port"))
        req = csrfProtect.embedInRequestCookie(baseReq, token)
        fiber <- resource
          .use(server =>
            IO.delay(println(s"Server started at ${server.address}!")) >> client.use(
              _.expect[String](req)))
          .start
        check <- fiber.join
      } yield check

    prg.assertEquals("CSRF passed!")
  }

}

object CSRFIntegrationSuite {

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
