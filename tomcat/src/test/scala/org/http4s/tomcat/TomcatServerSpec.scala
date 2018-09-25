package org.http4s
package server
package tomcat

import cats.effect.{IO, Timer}
import cats.implicits._
import java.io.IOException
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory
import org.http4s.dsl.io._
import org.specs2.concurrent.ExecutionEnv
import scala.concurrent.duration._
import scala.io.Source

class TomcatServerSpec(implicit ee: ExecutionEnv) extends {
  // Prevents us from loading jar and war URLs, but lets us
  // run Tomcat twice in the same JVM.  This makes me grumpy.
  //
  // Needs to run before the server is initialized in the superclass.
  // This also makes me grumpy.
  val _ = TomcatURLStreamHandlerFactory.disable()
} with Http4sSpec {
  def builder = TomcatBuilder[IO]

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
            implicitly[Timer[IO]].sleep(50.millis) *> Ok("slow")
        },
        "/"
      )
      .resource

  withResource(serverR) { server =>
    def get(path: String): IO[String] =
      contextShift.evalOn(testBlockingExecutionContext)(
        IO(
          Source
            .fromURL(new URL(s"http://127.0.0.1:${server.address.getPort}$path"))
            .getLines
            .mkString))

    def post(path: String, body: String): IO[String] =
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

      "Timeout" should {
        "not fire prematurely" in {
          get("/slow") must returnValue("slow")
        }

        "fire on timeout" in {
          get("/never").unsafeToFuture() must throwAn[IOException].awaitFor(5.seconds)
        }
      }
    }
  }
}
