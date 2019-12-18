package org.http4s
package server
package jetty

import java.net.{HttpURLConnection, URL}

import cats.data.Kleisli
import cats.effect.IO
import org.http4s.dsl.io._

import scala.io.Source
import scala.util.Try

class NotFoundHttpAppSpec extends Http4sSpec {

  def orMyHandler(x: HttpRoutes[IO]): HttpApp[IO] =
    Kleisli { req =>
      x.run(req).getOrElse(Response[IO](Status.NotFound).withEntity(s"Not Found ${req.uri.path}"))
    }

  val router = Router(
    "/prefix1" -> HttpRoutes.of[IO] {
      case GET -> Root / "hello" => Ok("hello")
    },
    "/prefix2" -> HttpRoutes.of[IO] {
      case GET -> Root / "hello2" => Ok("hello2")
    }
  )

  private val serverR =
    JettyBuilder[IO]
      .bindAny()
      .mountHttpApp(
        orMyHandler(router),
        "/"
      )
      .resource

  withResource(serverR) { server =>
    def get(path: String): IO[(Status, String)] = {
      val url = new URL(s"http://127.0.0.1:${server.address.getPort}$path")
      for {
        conn <- IO(url.openConnection().asInstanceOf[HttpURLConnection])
        _ <- IO(conn.setRequestMethod("GET"))
        isTry <- IO(Try(conn.getInputStream))
        // if getInputStream throws, then switch to getErrorStream
        is <- IO(isTry.fold(_ => conn.getErrorStream, is => is))
        resp <- IO(Source.fromInputStream(is).mkString)
        status <- IO.fromEither(Status.fromInt(conn.getResponseCode))
      } yield (status, resp)
    }

    "Server" should {
      "return NotFound on non-implemented endpoint /foo" in {
        get("/foo") must returnValue((Status.NotFound, "Not Found /foo"))
      }

      "return hello on endpoint /prefix1/hello" in {
        get("/prefix1/hello") must returnValue((Status.Ok, "hello"))
      }

      "return NotFound on non-implemented endpoint /prefix1/foo" in {
        get("/prefix1/foo") must returnValue((Status.NotFound, "Not Found /prefix1/foo"))
      }

      "return hello on endpoint /prefix2/hello2" in {
        get("/prefix2/hello2") must returnValue((Status.Ok, "hello2"))
      }

      "return NotFound on non-implemented endpoint /prefix2/foo2" in {
        get("/prefix2/foo2") must returnValue((Status.NotFound, "Not Found /prefix2/foo2"))
      }
    }
  }
}
