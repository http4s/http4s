package org.http4s
package server
package blaze

import cats.effect.IO
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import org.http4s.blaze.channel.ChannelOptions
import org.http4s.dsl.io._
import scala.concurrent.duration._
import scala.io.Source

class BlazeServerSpec extends Http4sSpec {

  def builder =
    BlazeServerBuilder[IO]
      .withResponseHeaderTimeout(1.second)
      .withExecutionContext(testExecutionContext)

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

    case _ => NotFound()
  }

  val serverR =
    builder
      .bindAny()
      .withHttpApp(service)
      .resource

  withResource(serverR) { server =>
    // This should be in IO and shifted but I'm tired of fighting this.
    def get(path: String): String =
      Source
        .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
        .getLines
        .mkString

    // This should be in IO and shifted but I'm tired of fighting this.
    def getStatus(path: String): IO[Status] = {
      val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
      for {
        conn <- IO(url.openConnection().asInstanceOf[HttpURLConnection])
        _ = conn.setRequestMethod("GET")
        status <- IO.fromEither(Status.fromInt(conn.getResponseCode()))
      } yield status
    }

    // This too
    def post(path: String, body: String): String = {
      val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Length", bytes.size.toString)
      conn.setDoOutput(true)
      conn.getOutputStream.write(bytes)
      Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines.mkString
    }

    "A server" should {
      "route requests on the service executor" in {
        get("/thread/routing") must startWith("http4s-spec-")
      }

      "execute the service task on the service executor" in {
        get("/thread/effect") must startWith("http4s-spec-")
      }

      "be able to echo its input" in {
        val input = """{ "Hello": "world" }"""
        post("/echo", input) must startWith(input)
      }

      "return a 503 if the server doesn't respond" in {
        getStatus("/never") must returnValue(Status.ServiceUnavailable)
      }
    }
  }

  "ChannelOptions" should {
    "default to empty" in {
      builder.channelOptions must_== ChannelOptions(Vector.empty)
    }
    "set socket send buffer size" in {
      builder.withSocketSendBufferSize(8192).socketSendBufferSize must beSome(8192)
    }
    "set socket receive buffer size" in {
      builder.withSocketReceiveBufferSize(8192).socketReceiveBufferSize must beSome(8192)
    }
    "set socket keepalive" in {
      builder.withSocketKeepAlive(true).socketKeepAlive must beSome(true)
    }
    "set socket reuse address" in {
      builder.withSocketReuseAddress(true).socketReuseAddress must beSome(true)
    }
    "set TCP nodelay" in {
      builder.withTcpNoDelay(true).tcpNoDelay must beSome(true)
    }
    "unset socket send buffer size" in {
      builder
        .withSocketSendBufferSize(8192)
        .withDefaultSocketSendBufferSize
        .socketSendBufferSize must beNone
    }
    "unset socket receive buffer size" in {
      builder
        .withSocketReceiveBufferSize(8192)
        .withDefaultSocketReceiveBufferSize
        .socketReceiveBufferSize must beNone
    }
    "unset socket keepalive" in {
      builder.withSocketKeepAlive(true).withDefaultSocketKeepAlive.socketKeepAlive must beNone
    }
    "unset socket reuse address" in {
      builder
        .withSocketReuseAddress(true)
        .withDefaultSocketReuseAddress
        .socketReuseAddress must beNone
    }
    "unset TCP nodelay" in {
      builder.withTcpNoDelay(true).withDefaultTcpNoDelay.tcpNoDelay must beNone
    }
    "overwrite keys" in {
      builder
        .withSocketSendBufferSize(8192)
        .withSocketSendBufferSize(4096)
        .socketSendBufferSize must beSome(4096)
    }
  }
}
