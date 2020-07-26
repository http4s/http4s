/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package armeria

import cats.implicits._
import cats.effect.{IO, Resource}
import com.linecorp.armeria.server.logging.{ContentPreviewingService, LoggingService}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import org.http4s.Http4sSpec
import org.http4s.dsl.io._
import org.http4s.multipart.Multipart
import org.http4s.testing.Http4sLegacyMatchersIO
import org.specs2.execute.Result
import scala.io.Source

class ArmeriaServerBuilderSpec extends Http4sSpec with Http4sLegacyMatchersIO {

  val service: HttpRoutes[IO] = HttpRoutes.of {
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
        Ok.apply(mp.parts.foldMap(_.body))
      }

    case _ => NotFound()
  }

  val serverR: Resource[IO, Server] = ArmeriaServerBuilder[IO]
    .withDecorator(ContentPreviewingService.newDecorator(Int.MaxValue))
    .withDecorator(LoggingService.newDecorator())
    .bindAny()
    .withHttpRoutes("/service", service)
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

    // This too
    def postChunkedMultipart(path: String, boundary: String, body: String): IO[String] =
      IO {
        val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
        val conn = url.openConnection().asInstanceOf[HttpURLConnection]
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        conn.setRequestMethod("POST")
        conn.setChunkedStreamingMode(-1)
        conn.setRequestProperty("Content-Type", s"""multipart/form-data; boundary="$boundary"""")
        conn.setDoOutput(true)
        conn.getOutputStream.write(bytes)
        Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines.mkString
      }

    "A server" should {
      "route requests on the service executor" in {
        // A event loop will serve the service to reduce an extra context switching
        get("/service/thread/routing") must startWith("armeria-common-worker-nio")
      }

      "execute the service task on the service executor" in {
        // A event loop will serve the service to reduce an extra context switching
        get("/service/thread/effect") must startWith("armeria-common-worker-nio")
      }

      "be able to echo its input" in {
        val input = """{ "Hello": "world" }"""
        post("/service/echo", input) must startWith(input)
      }

      "return a 503 if the server doesn't respond" in {
        getStatus("/service/never") must returnValue(Status.ServiceUnavailable)
      }

      "reliably handle multipart requests" in {
        val body =
          """|--aa
             |Content-Disposition: form-data; name="a"
             |Content-Length: 1
             |
             |a
             |--aa--""".stripMargin.replace("\n", "\r\n")

        Result.foreach(1 to 100) { _ =>
          postChunkedMultipart(
            "/service/issue2610",
            "aa",
            body
          ) must returnValue("a")
        }
      }
    }
  }
}
