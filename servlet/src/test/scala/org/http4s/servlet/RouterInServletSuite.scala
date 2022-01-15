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

package org.http4s.servlet

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Dispatcher
import org.http4s.Http4sSuite
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.server.Router
import org.http4s.testing.AutoCloseableResource

import java.net.URL
import scala.io.Source

// Regression tests for #5362 / #5100
class RouterInServletSuite extends Http4sSuite {

  private val mainRoutes = HttpRoutes.of[IO] {
    case GET -> Root => Ok("root")
    case GET -> Root / "suffix" => Ok("suffix")
  }

  private val alternativeRoutes = HttpRoutes.of[IO] { case GET -> Root =>
    Ok("alternative root")
  }

  private val router = Router(
    "prefix" -> mainRoutes,
    "" -> alternativeRoutes,
  )

  private val serverWithoutRouter =
    ResourceFixture[Int](Dispatcher[IO].flatMap(d => mkServer(mainRoutes, dispatcher = d)))
  private val server =
    ResourceFixture[Int](Dispatcher[IO].flatMap(d => mkServer(router, dispatcher = d)))
  private val serverWithContextPath =
    ResourceFixture[Int](
      Dispatcher[IO].flatMap(d => mkServer(router, contextPath = "/context", dispatcher = d))
    )
  private val serverWithServletPath =
    ResourceFixture[Int](
      Dispatcher[IO].flatMap(d => mkServer(router, servletPath = "/servlet/*", dispatcher = d))
    )
  private val serverWithContextAndServletPath =
    ResourceFixture[Int](
      Dispatcher[IO].flatMap(d =>
        mkServer(router, contextPath = "/context", servletPath = "/servlet/*", dispatcher = d)
      )
    )

  serverWithoutRouter.test(
    "Http4s servlet without router should handle root request"
  )(server => get(server, "").assertEquals("root"))

  serverWithoutRouter.test(
    "Http4s servlet without router should handle suffix request"
  )(server => get(server, "suffix").assertEquals("suffix"))

  server.test(
    "Http4s servlet should handle alternative-root request"
  )(server => get(server, "").assertEquals("alternative root"))

  server.test(
    "Http4s servlet should handle root request"
  )(server => get(server, "prefix").assertEquals("root"))

  server.test(
    "Http4s servlet should handle suffix request"
  )(server => get(server, "prefix/suffix").assertEquals("suffix"))

  serverWithContextPath.test(
    "Http4s servlet with non-empty context path should handle alternative-root request"
  )(server => get(server, "context").assertEquals("alternative root"))

  serverWithContextPath.test(
    "Http4s servlet with non-empty context path should handle root request"
  )(server => get(server, "context/prefix").assertEquals("root"))

  serverWithContextPath.test(
    "Http4s servlet with non-empty context path should handle suffix request"
  )(server => get(server, "context/prefix/suffix").assertEquals("suffix"))

  serverWithServletPath.test(
    "Http4s servlet with non-empty servlet path should handle alternative-root request"
  )(server => get(server, "servlet").assertEquals("alternative root"))

  serverWithServletPath.test(
    "Http4s servlet with non-empty servlet path should handle root request"
  )(server => get(server, "servlet/prefix").assertEquals("root"))

  serverWithServletPath.test(
    "Http4s servlet with non-empty servlet path should handle suffix request"
  )(server => get(server, "servlet/prefix/suffix").assertEquals("suffix"))

  serverWithContextAndServletPath.test(
    "Http4s servlet with non-empty context & servlet path should handle alternative-root request"
  )(server => get(server, "context/servlet").assertEquals("alternative root"))

  serverWithContextAndServletPath.test(
    "Http4s servlet with non-empty context & servlet path should handle root request"
  )(server => get(server, "context/servlet/prefix").assertEquals("root"))

  serverWithContextAndServletPath.test(
    "Http4s servlet with non-empty context & servlet path should handle suffix request"
  )(server => get(server, "context/servlet/prefix/suffix").assertEquals("suffix"))

  private def get(serverPort: Int, path: String): IO[String] =
    IO.delay(
      AutoCloseableResource.resource(
        Source
          .fromURL(new URL(s"http://127.0.0.1:$serverPort/$path"))
      )(_.getLines().mkString)
    )

  private def mkServer(
      routes: HttpRoutes[IO],
      contextPath: String = "/",
      servletPath: String = "/*",
      dispatcher: Dispatcher[IO],
  ): Resource[IO, Int] = TestEclipseServer(servlet(routes, dispatcher), contextPath, servletPath)

  private def servlet(routes: HttpRoutes[IO], dispatcher: Dispatcher[IO]) =
    new BlockingHttp4sServlet[IO](
      service = routes.orNotFound,
      servletIo = org.http4s.servlet.BlockingServletIo(4096),
      serviceErrorHandler = DefaultServiceErrorHandler,
      dispatcher = dispatcher,
    )

}
