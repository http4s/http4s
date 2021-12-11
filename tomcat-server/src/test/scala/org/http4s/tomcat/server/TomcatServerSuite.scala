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
package tomcat
package server

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import cats.syntax.all._
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.testing.AutoCloseableResource

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.logging.LogManager
import scala.concurrent.duration._
import scala.io.Source

class TomcatServerSuite extends Http4sSuite {
  implicit val contextShift: ContextShift[IO] = Http4sSuite.TestContextShift

  override def beforeEach(context: BeforeEach): Unit = {
    // Prevents us from loading jar and war URLs, but lets us
    // run Tomcat twice in the same JVM.  This makes me grumpy.
    //
    TomcatURLStreamHandlerFactory.disable()
    LogManager.getLogManager().reset()
  }

  private val builder = TomcatBuilder[IO]

  private val serverR: cats.effect.Resource[IO, Server] =
    builder
      .bindAny()
      .withAsyncTimeout(3.seconds)
      .mountService(
        HttpRoutes.of {
          case GET -> Root / "thread" / "routing" =>
            val thread = Thread.currentThread.getName
            Ok(thread)

          case GET -> Root / "thread" / "effect" =>
            IO(Thread.currentThread.getName).flatMap(Ok(_))

          case req @ POST -> Root / "echo" =>
            Ok(req.body)

          case GET -> Root / "never" =>
            IO.never

          case GET -> Root / "slow" =>
            implicitly[Timer[IO]].sleep(50.millis) *> Ok("slow")
        },
        "/",
      )
      .resource

  private val tomcatServer = ResourceFixture[Server](serverR)

  private def get(server: Server, path: String): IO[String] =
    testBlocker.blockOn(
      IO(
        AutoCloseableResource.resource(
          Source
            .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
        )(_.getLines().mkString)
      )
    )

  private def post(server: Server, path: String, body: String): IO[String] =
    testBlocker.blockOn(IO {
      val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Length", bytes.size.toString)
      conn.setDoOutput(true)
      conn.getOutputStream.write(bytes)

      AutoCloseableResource.resource(
        Source
          .fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name)
      )(_.getLines().mkString)
    })

  tomcatServer.test("server should route requests on the service executor".flaky) { server =>
    val prefix: String = "http4s-suite-"
    get(server, "/thread/routing")
      .map(_.take(prefix.size))
      .assertEquals(prefix)
  }

  tomcatServer.test("server should execute the service task on the service executor".flaky) {
    server =>
      val prefix: String = "http4s-suite-"
      get(server, "/thread/effect").map(_.take(prefix.size)).assertEquals(prefix)
  }

  tomcatServer.test("server should be able to echo its input") { server =>
    val input = """{ "Hello": "world" }"""
    post(server, "/echo", input).map(_.take(input.size)).assertEquals(input)
  }

  tomcatServer.test("Timeout should not fire prematurely") { server =>
    get(server, "/slow").assertEquals("slow")
  }

  tomcatServer.test("Timeout should fire on timeout") { server =>
    get(server, "/never").intercept[IOException]
  }
}
