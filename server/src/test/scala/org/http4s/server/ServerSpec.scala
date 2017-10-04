package org.http4s
package server

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import fs2.Task
import org.http4s.dsl._
import org.specs2.specification.AfterAll
import scala.io.Source

trait ServerSpec extends Http4sSpec with AfterAll {
  def builder: ServerBuilder

  val server =
    builder
      .bindAny()
      .withExecutionContext(Http4sSpec.TestExecutionContext)
      .mountService(HttpService {
        case GET -> Root / "thread" / "routing" =>
          val thread = Thread.currentThread.getName
          Ok(thread)

        case GET -> Root / "thread" / "effect" =>
          Task.delay(Thread.currentThread.getName).flatMap(Ok(_))

        case req @ POST -> Root / "echo" =>
          Ok(req.body)
      })
      .start
      .unsafeRun()

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
    Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name)
      .getLines
      .mkString
  }

  "A server" should {
    val globalExecutorThreadPrefix = BuildInfo.scalaVersion match {
      case v if v.startsWith("2.11.") => "ForkJoinPool-"
      case _ => "scala-execution-context-global-"
    }

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
