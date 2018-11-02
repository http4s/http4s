package org.http4s
package server
package staticcontent

import cats.effect._
import org.http4s.Method.{GET, POST}
import org.http4s.Uri.uri
import org.http4s.server.staticcontent.WebjarService.Config

object WebjarServiceSpec extends Http4sSpec with StaticContentShared {

  def routes: HttpRoutes[IO] =
    webjarService(Config[IO](blockingExecutionContext = testBlockingExecutionContext))

  "The WebjarService" should {

    "Return a 200 Ok file" in {
      val req = Request[IO](GET, Uri(path = "/test-lib/1.0.0/testresource.txt"))
      val rb = runReq(req)

      rb._1 must_== testWebjarResource
      rb._2.status must_== Status.Ok
    }

    "Return a 200 Ok file in a subdirectory" in {
      val req = Request[IO](GET, Uri(path = "/test-lib/1.0.0/sub/testresource.txt"))
      val rb = runReq(req)

      rb._1 must_== testWebjarSubResource
      rb._2.status must_== Status.Ok
    }

    "Not find missing file" in {
      val req = Request[IO](uri = uri("/test-lib/1.0.0/doesnotexist.txt"))
      routes.apply(req).value must returnValue(None)
    }

    "Not find missing library" in {
      val req = Request[IO](uri = uri("/1.0.0/doesnotexist.txt"))
      routes.apply(req).value must returnValue(None)
    }

    "Not find missing version" in {
      val req = Request[IO](uri = uri("/test-lib//doesnotexist.txt"))
      routes.apply(req).value must returnValue(None)
    }

    "Not find missing asset" in {
      val req = Request[IO](uri = uri("/test-lib/1.0.0/"))
      routes.apply(req).value must returnValue(None)
    }

    "Not match a request with POST" in {
      val req = Request[IO](POST, Uri(path = "/test-lib/1.0.0/testresource.txt"))
      routes.apply(req).value must returnValue(None)
    }
  }
}
