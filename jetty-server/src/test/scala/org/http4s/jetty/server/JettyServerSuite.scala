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

import cats.effect.{IO, Temporal}
import cats.syntax.all._
import java.net.{HttpURLConnection, URL}
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.http4s.dsl.io._
import org.http4s.server.Server
import scala.concurrent.duration._
import scala.io.Source

class JettyServerSuite extends Http4sSuite {

  def builder = JettyBuilder[IO]

  val serverR =
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
            Temporal[IO].sleep(50.millis) *> Ok("slow")
        },
        "/"
      )
      .resource

  val jettyServer = ResourceFixture[Server](serverR)

  def get(server: Server, path: String): IO[String] =
    IO.blocking(
      Source
        .fromURL(new URL(s"http://${server.address}$path"))
        .getLines()
        .mkString)

  def post(server: Server, path: String, body: String): IO[String] =
    IO.blocking {
      val url = new URL(s"http://${server.address}$path")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Length", bytes.size.toString)
      conn.setDoOutput(true)
      conn.getOutputStream.write(bytes)
      Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines().mkString
    }

  jettyServer.test("ChannelOptions should route requests on the service executor") { server =>
    get(server, "/thread/routing").map(_.startsWith("http4s-suite-")).assert
  }

  jettyServer.test("ChannelOptions should execute the service task on the service executor") {
    server =>
      get(server, "/thread/effect").map(_.startsWith("http4s-suite-")).assert
  }

  jettyServer.test("ChannelOptions should be able to echo its input") { server =>
    val input = """{ "Hello": "world" }"""
    post(server, "/echo", input).map(_.startsWith(input)).assert
  }

  jettyServer.test("Timeout should not fire prematurely") { server =>
    get(server, "/slow").assertEquals("slow")
  }

  jettyServer.test("Timeout should fire on timeout".flaky) { server =>
    get(server, "/never").intercept[IOException]
  }

  jettyServer.test("Timeout should execute the service task on the service executor") { server =>
    get(server, "/thread/effect").map(_.startsWith("http4s-suite-")).assert
  }
}
