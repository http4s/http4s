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
package server
package tomcat

import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.all._
import java.io.IOException
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory
import org.http4s.dsl.io._
import scala.concurrent.duration._
import scala.io.Source
import java.util.logging.LogManager

class TomcatServerSuite extends Http4sSuite {
  implicit val contextShift: ContextShift[IO] = Http4sSpec.TestContextShift

  override def beforeEach(context: BeforeEach): Unit = {
    // Prevents us from loading jar and war URLs, but lets us
    // run Tomcat twice in the same JVM.  This makes me grumpy.
    //
    TomcatURLStreamHandlerFactory.disable()
    LogManager.getLogManager().reset()
  }

  val builder = TomcatBuilder[IO]

  val serverR: cats.effect.Resource[IO, Server] =
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
        "/"
      )
      .resource

  def tomcatServer: FunFixture[Server] =
    ResourceFixture[Server](serverR)

  def get(server: Server, path: String): IO[String] =
    testBlocker.blockOn(
      IO(
        Source
          .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
          .getLines()
          .mkString))

  def post(server: Server, path: String, body: String): IO[String] =
    testBlocker.blockOn(IO {
      val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Length", bytes.size.toString)
      conn.setDoOutput(true)
      conn.getOutputStream.write(bytes)
      Source
        .fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name)
        .getLines()
        .mkString
    })

  tomcatServer.test("server should route requests on the service executor") { server =>
    get(server, "/thread/routing")
      .map(_.startsWith("http4s-spec-"))
      .assertEquals(true)
  }

  tomcatServer.test("server should execute the service task on the service executor") { server =>
    get(server, "/thread/effect").map(_.startsWith("http4s-spec-")).assertEquals(true)
  }

  tomcatServer.test("server should be able to echo its input") { server =>
    val input = """{ "Hello": "world" }"""
    post(server, "/echo", input).map(_.startsWith(input)).assertEquals(true)
  }

  tomcatServer.test("Timeout should not fire prematurely") { server =>
    get(server, "/slow").assertEquals("slow")
  }

  tomcatServer.test("Timeout should fire on timeout") { server =>
    get(server, "/never").intercept[IOException]
  }
}
