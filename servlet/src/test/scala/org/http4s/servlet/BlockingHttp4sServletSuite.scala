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

package org.http4s
package servlet

import cats.effect.IO
import cats.effect.Resource
import cats.effect.Timer
import cats.syntax.all._
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.{Server => EclipseServer}
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.http4s.dsl.io._
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.syntax.all._
import org.http4s.testing.ClosableResource

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.io.Source

class BlockingHttp4sServletSuite extends Http4sSuite {
  private lazy val service = HttpRoutes
    .of[IO] {
      case GET -> Root / "simple" =>
        Ok("simple")
      case req @ POST -> Root / "echo" =>
        Ok(req.body)
      case GET -> Root / "shifted" =>
        IO.shift(munitExecutionContext) *>
          // Wait for a bit to make sure we lose the race
          Timer[IO].sleep(50.millis) *>
          Ok("shifted")
    }
    .orNotFound

  private val servletServer = ResourceFixture[Int](serverPortR)

  private def get(serverPort: Int, path: String): IO[String] =
    testBlocker.delay[IO, String](
      ClosableResource.resource(
        Source
          .fromURL(new URL(s"http://127.0.0.1:$serverPort/$path"))
      )(_.getLines().mkString)(_.close())
    )

  private def post(serverPort: Int, path: String, body: String): IO[String] =
    testBlocker.delay[IO, String] {
      val url = new URL(s"http://127.0.0.1:$serverPort/$path")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Length", bytes.size.toString)
      conn.setDoOutput(true)
      conn.getOutputStream.write(bytes)

      ClosableResource.resource(
        Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name)
      )(_.getLines().mkString)(_.close())
    }

  servletServer.test("Http4sBlockingServlet handle GET requests") { server =>
    get(server, "simple").assertEquals("simple")
  }

  servletServer.test("Http4sBlockingServlet handle POST requests") { server =>
    post(server, "echo", "input data").assertEquals("input data")
  }

  servletServer.test("Http4sBlockingServlet work for shifted IO") { server =>
    get(server, "shifted").assertEquals("shifted")
  }

  private lazy val servlet = new BlockingHttp4sServlet[IO](
    service = service,
    servletIo = org.http4s.servlet.BlockingServletIo(4096, testBlocker),
    serviceErrorHandler = DefaultServiceErrorHandler,
  )

  private lazy val serverPortR = Resource
    .make(IO(new EclipseServer))(server => IO(server.stop()))
    .evalMap { server =>
      IO {
        val connector =
          new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()))

        val context = new ServletContextHandler
        context.addServlet(new ServletHolder(servlet), "/*")

        server.addConnector(connector)
        server.setHandler(context)

        server.start()

        connector.getLocalPort
      }
    }
}
