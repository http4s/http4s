package org.http4s
package server
package jetty

import cats.effect.IO
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import org.http4s.dsl.io._
import org.specs2.specification.AfterAll
import scala.io.Source

class JettyServerSpec extends Http4sSpec with AfterAll {
  def builder = JettyBuilder[IO]

  val server =
    builder
      .bindAny()
      .mountService(
        HttpRoutes.of {
          case GET -> Root / "thread" / "routing" =>
            val thread = Thread.currentThread.getName
            Ok(thread)

          case GET -> Root / "thread" / "effect" =>
            IO(Thread.currentThread.getName).flatMap(Ok(_))

          case req @ POST -> Root / "echo" =>
            Ok(req.body)
        },
        "/"
      )
      .start
      .unsafeRunSync()

  def afterAll = server.shutdownNow()

  // This should be in IO and shifted but I'm tired of fighting this.
  private def get(path: String): String =
    Source
      .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
      .getLines
      .mkString

  // This too
  private def post(path: String, body: String): String = {
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
  }
}
