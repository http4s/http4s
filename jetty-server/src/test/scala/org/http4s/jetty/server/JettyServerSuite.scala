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
package jetty
package server

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import cats.syntax.all._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.testing.AutoCloseableResource

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.io.Source

class JettyServerSuite extends Http4sSuite {
  implicit val contextShift: ContextShift[IO] = Http4sSuite.TestContextShift

  private def builder = JettyBuilder[IO]

  private val serverR =
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

  private val jettyServer = ResourceFixture[Server](serverR)

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
        Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name)
      )(_.getLines().mkString)
    })

  jettyServer.test("ChannelOptions should should route requests on the service executor") {
    server =>
      get(server, "/thread/routing").map(_.startsWith("http4s-suite-")).assert
  }

  jettyServer.test(
    "ChannelOptions should should execute the service task on the service executor"
  ) { server =>
    get(server, "/thread/effect").map(_.startsWith("http4s-suite-")).assert
  }

  jettyServer.test("ChannelOptions should be able to echo its input") { server =>
    val input = """{ "Hello": "world" }"""
    post(server, "/echo", input).map(_.startsWith(input)).assert
  }

  jettyServer.test("Timeout not fire prematurely") { server =>
    get(server, "/slow").assertEquals("slow")
  }

  jettyServer.test("Timeout should fire on timeout".flaky) { server =>
    get(server, "/never").intercept[IOException]
  }

  jettyServer.test("Timeout should execute the service task on the service executor") { server =>
    get(server, "/thread/effect").map(_.startsWith("http4s-suite-")).assert
  }
}
