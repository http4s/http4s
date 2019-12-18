package org.http4s
package servlet

import cats.implicits._
import cats.effect.{IO, Resource, Timer}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import org.eclipse.jetty.server.{HttpConfiguration, HttpConnectionFactory, Server, ServerConnector}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.http4s.dsl.io._
import org.http4s.server.DefaultServiceErrorHandler
import scala.io.Source
import scala.concurrent.duration._

class BlockingHttp4sServletSpec extends Http4sSpec {

  lazy val service = HttpRoutes
    .of[IO] {
      case GET -> Root / "simple" =>
        Ok("simple")
      case req @ POST -> Root / "echo" =>
        Ok(req.body)
      case GET -> Root / "shifted" =>
        IO.shift(testExecutionContext) *>
          // Wait for a bit to make sure we lose the race
          Timer[IO].sleep(50.millis) *>
          Ok("shifted")
    }
    .orNotFound

  withResource(serverPortR) { serverPort =>
    def get(path: String): IO[String] =
      testBlocker.delay(
        Source
          .fromURL(new URL(s"http://127.0.0.1:$serverPort/$path"))
          .getLines
          .mkString)

    def post(path: String, body: String): IO[String] =
      testBlocker.delay {
        val url = new URL(s"http://127.0.0.1:$serverPort/$path")
        val conn = url.openConnection().asInstanceOf[HttpURLConnection]
        val bytes = body.getBytes(StandardCharsets.UTF_8)
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Content-Length", bytes.size.toString)
        conn.setDoOutput(true)
        conn.getOutputStream.write(bytes)
        Source.fromInputStream(conn.getInputStream, StandardCharsets.UTF_8.name).getLines.mkString
      }

    "Http4sBlockingServlet" should {
      "handle GET requests" in {
        get("simple") must returnValue("simple")
      }

      "handle POST requests" in {
        post("echo", "input data") must returnValue("input data")
      }

      "work for shifted IO" in {
        get("shifted") must returnValue("shifted")
      }
    }
  }

  lazy val servlet = new BlockingHttp4sServlet[IO](
    service = service,
    servletIo = org.http4s.servlet.BlockingServletIo(4096, testBlocker),
    serviceErrorHandler = DefaultServiceErrorHandler
  )

  lazy val serverPortR = Resource
    .make(IO { new Server })(server => IO { server.stop() })
    .evalMap { server =>
      IO {
        val connector =
          new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()))

        val context = new ServletContextHandler
        context.addServlet(new ServletHolder(servlet), "/*")

        server.addConnector(connector)
        server.setHandler(context)

        server.start()

        connector.getLocalPort
      }
    }
}
