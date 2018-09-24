package org.http4s
package server
package jetty

import cats.effect.{IO, Timer}
import cats.implicits._
import java.net.{HttpURLConnection, URL}
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.http4s.dsl.io._
import org.specs2.specification.AfterAll
import scala.concurrent.duration._
import scala.io.Source

class JettyServerSpec extends Http4sSpec with AfterAll {
  def builder = JettyBuilder[IO]

  val server =
    builder
      .bindAny()
      .withAsyncTimeout(500.millis)
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
      .start
      .unsafeRunSync()

  def afterAll = server.shutdownNow()

  private def get(path: String): IO[String] =
    contextShift.evalOn(testBlockingExecutionContext)(
      IO(
        Source
          .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
          .getLines
          .mkString))

  private def post(path: String, body: String): IO[String] =
    contextShift.evalOn(testBlockingExecutionContext)(IO {
      val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
      val conn = url.openConnection().asInstanceOf[HttpURLConnection]
      val bytes = body.getBytes(StandardCharsets.UTF_8)
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Length", bytes.size.toString)
      conn.setDoOutput(true)
      conn.getOutputStream.write(bytes)
      Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines.mkString
    })

  "A server" should {
    "route requests on the service executor" in {
      get("/thread/routing") must returnValue(startWith("http4s-spec-"))
    }

    "execute the service task on the service executor" in {
      get("/thread/effect") must returnValue(startWith("http4s-spec-"))
    }

    "be able to echo its input" in {
      val input = """{ "Hello": "world" }"""
      post("/echo", input) must returnValue(startWith(input))
    }
  }

  "Timeout" should {
    "not fire prematurely" in {
      get("/slow") must returnValue("slow")
    }

    "fire on timeout" in {
      get("/never").unsafeRunSync() must throwAn[IOException]
    }
  }
}
