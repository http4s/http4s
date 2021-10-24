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
package blaze
package server

import cats.effect._
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.syntax.all._
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.dsl.io._
import org.http4s.internal.threads._
import scala.concurrent.duration._
import scala.io.Source
import org.http4s.multipart.Multipart
import org.http4s.server.Server
import scala.concurrent.ExecutionContext
import munit.TestOptions

class BlazeServerSuite extends Http4sSuite {

  override implicit lazy val munitIoRuntime: IORuntime = {
    val TestScheduler: ScheduledExecutorService = {
      val s =
        new ScheduledThreadPoolExecutor(
          2,
          threadFactory(i => s"blaze-server-suite-scheduler-$i", true))
      s.setKeepAliveTime(10L, TimeUnit.SECONDS)
      s.allowCoreThreadTimeOut(true)
      s
    }

    val blockingPool = newBlockingPool("blaze-server-suite-blocking")
    val computePool = newDaemonPool("blaze-server-suite-compute", timeout = true)
    val scheduledExecutor = TestScheduler
    IORuntime.apply(
      ExecutionContext.fromExecutor(computePool),
      ExecutionContext.fromExecutor(blockingPool),
      Scheduler.fromScheduledExecutor(scheduledExecutor),
      () => {
        blockingPool.shutdown()
        computePool.shutdown()
        scheduledExecutor.shutdown()
      },
      IORuntimeConfig()
    )
  }

  override def afterAll(): Unit = munitIoRuntime.shutdown()

  def builder =
    BlazeServerBuilder[IO]
      .withResponseHeaderTimeout(1.second)

  val service: HttpApp[IO] = HttpApp {
    case GET -> Root / "thread" / "routing" =>
      val thread = Thread.currentThread.getName
      Ok(thread)

    case GET -> Root / "thread" / "effect" =>
      IO(Thread.currentThread.getName).flatMap(Ok(_))

    case req @ POST -> Root / "echo" =>
      Ok(req.body)

    case _ -> Root / "never" =>
      IO.never

    case req @ POST -> Root / "issue2610" =>
      req.decode[Multipart[IO]] { mp =>
        Ok(mp.parts.foldMap(_.body))
      }

    case _ => NotFound()
  }

  val serverR =
    builder
      .bindAny()
      .withHttpApp(service)
      .resource

  val blazeServer =
    ResourceFixture[Server](
      serverR,
      (_: TestOptions, _: Server) => IO.unit,
      (_: Server) => IO.sleep(100.milliseconds) *> IO.unit)

  def get(server: Server, path: String): IO[String] = IO.blocking {
    Source
      .fromURL(new URL(s"http://${server.address}$path"))
      .getLines()
      .mkString
  }

  def getStatus(server: Server, path: String): IO[Status] = {
    val url = new URL(s"http://${server.address}$path")
    for {
      conn <- IO.blocking(url.openConnection().asInstanceOf[HttpURLConnection])
      _ = conn.setRequestMethod("GET")
      status <- IO
        .blocking(conn.getResponseCode())
        .flatMap(code => IO.fromEither(Status.fromInt(code)))
    } yield status
  }

  def post(server: Server, path: String, body: String): IO[String] = IO.blocking {
    val url = new URL(s"http://${server.address}$path")
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Length", bytes.size.toString)
    conn.setDoOutput(true)
    conn.getOutputStream.write(bytes)
    Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines().mkString
  }

  def postChunkedMultipart(
      server: Server,
      path: String,
      boundary: String,
      body: String): IO[String] =
    IO.blocking {
      val url = new URL(s"http://${server.address}$path")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      conn.setRequestMethod("POST")
      conn.setChunkedStreamingMode(-1)
      conn.setRequestProperty("Content-Type", s"""multipart/form-data; boundary="$boundary"""")
      conn.setDoOutput(true)
      conn.getOutputStream.write(bytes)
      Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines().mkString
    }

  blazeServer.test("route requests on the service executor".flaky) { server =>
    get(server, "/thread/routing").map(_.startsWith("blaze-server-suite-compute-")).assert
  }

  blazeServer.test("execute the service task on the service executor") { server =>
    get(server, "/thread/effect").map(_.startsWith("blaze-server-suite-compute-")).assert
  }

  blazeServer.test("be able to echo its input") { server =>
    val input = """{ "Hello": "world" }"""
    post(server, "/echo", input).map(_.startsWith(input)).assert
  }

  blazeServer.test("return a 503 if the server doesn't respond") { server =>
    getStatus(server, "/never").assertEquals(Status.ServiceUnavailable)
  }

  blazeServer.test("reliably handle multipart requests") { server =>
    val body =
      """|--aa
             |server: Server, Content-Disposition: form-data; name="a"
             |Content-Length: 1
             |
             |a
             |--aa--""".stripMargin.replace("\n", "\r\n")

    // This is flaky due to Blaze threading and Java connection pooling.
    (1 to 100).toList.traverse { _ =>
      postChunkedMultipart(server, "/issue2610", "aa", body).assertEquals("a")
    }
  }

  blazeServer.test("ChannelOptions should default to empty") { _ =>
    assertEquals(builder.channelOptions, ChannelOptions(Vector.empty))
  }
  blazeServer.test("ChannelOptions should set socket send buffer size") { _ =>
    assertEquals(builder.withSocketSendBufferSize(8192).socketSendBufferSize, Some(8192))
  }
  blazeServer.test("ChannelOptions should set socket receive buffer size") { _ =>
    assertEquals(builder.withSocketReceiveBufferSize(8192).socketReceiveBufferSize, Some(8192))
  }
  blazeServer.test("ChannelOptions should set socket keepalive") { _ =>
    assertEquals(builder.withSocketKeepAlive(true).socketKeepAlive, Some(true))
  }
  blazeServer.test("ChannelOptions should set socket reuse address") { _ =>
    assertEquals(builder.withSocketReuseAddress(true).socketReuseAddress, Some(true))
  }
  blazeServer.test("ChannelOptions should set TCP nodelay") { _ =>
    assertEquals(builder.withTcpNoDelay(true).tcpNoDelay, Some(true))
  }
  blazeServer.test("ChannelOptions should unset socket send buffer size") { _ =>
    assertEquals(
      builder
        .withSocketSendBufferSize(8192)
        .withDefaultSocketSendBufferSize
        .socketSendBufferSize,
      None)
  }
  blazeServer.test("ChannelOptions should unset socket receive buffer size") { _ =>
    assertEquals(
      builder
        .withSocketReceiveBufferSize(8192)
        .withDefaultSocketReceiveBufferSize
        .socketReceiveBufferSize,
      None)
  }
  blazeServer.test("ChannelOptions should unset socket keepalive") { _ =>
    assertEquals(builder.withSocketKeepAlive(true).withDefaultSocketKeepAlive.socketKeepAlive, None)
  }
  blazeServer.test("ChannelOptions should unset socket reuse address") { _ =>
    assertEquals(
      builder
        .withSocketReuseAddress(true)
        .withDefaultSocketReuseAddress
        .socketReuseAddress,
      None)
  }
  blazeServer.test("ChannelOptions should unset TCP nodelay") { _ =>
    assertEquals(builder.withTcpNoDelay(true).withDefaultTcpNoDelay.tcpNoDelay, None)
  }
  blazeServer.test("ChannelOptions should overwrite keys") { _ =>
    assertEquals(
      builder
        .withSocketSendBufferSize(8192)
        .withSocketSendBufferSize(4096)
        .socketSendBufferSize,
      Some(4096))
  }
}
