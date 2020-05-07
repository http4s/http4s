package org.http4s
package server
package staticcontent

import cats.effect.IO
import java.nio.file.Paths
import org.http4s.Method.{GET, POST}
import org.http4s.Uri.uri
import org.http4s.server.staticcontent.WebjarService.Config
import org.http4s.testing.Http4sLegacyMatchersIO

object WebjarServiceSpec extends Http4sSpec with StaticContentShared with Http4sLegacyMatchersIO {
  def routes: HttpRoutes[IO] =
    webjarService(Config[IO](blocker = testBlocker))
  val defaultBase =
    test.BuildInfo.test_resourceDirectory.toPath.resolve("META-INF/resources/webjars").toString

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

    "Decodes path segments" in {
      val req = Request[IO](uri = uri("/deep+purple/machine+head/space+truckin%27.txt"))
      routes.orNotFound(req) must returnStatus(Status.Ok)
    }

    "Return a 400 on a relative link even if it's inside the context" in {
      val relativePath = "test-lib/1.0.0/sub/../testresource.txt"
      val file = Paths.get(defaultBase).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      routes.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Return a 400 if the request tries to escape the context" in {
      val relativePath = "../../../testresource.txt"
      val file = Paths.get(defaultBase).resolve(relativePath).toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("/" + relativePath)
      val req = Request[IO](uri = uri)
      routes.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Return a 400 if the request tries to escape the context with /" in {
      val absPath = Paths.get(defaultBase).resolve("test-lib/1.0.0/testresource.txt")
      val file = absPath.toFile
      file.exists() must beTrue

      val uri = Uri.unsafeFromString("///" + absPath)
      val req = Request[IO](uri = uri)
      routes.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Not find missing file" in {
      val req = Request[IO](uri = uri("/test-lib/1.0.0/doesnotexist.txt"))
      routes.apply(req).value must returnValue(Option.empty[Response[IO]])
    }

    "Not find missing library" in {
      val req = Request[IO](uri = uri("/1.0.0/doesnotexist.txt"))
      routes.apply(req).value must returnValue(Option.empty[Response[IO]])
    }

    "Return bad request on missing version" in {
      val req = Request[IO](uri = uri("/test-lib//doesnotexist.txt"))
      routes.orNotFound(req) must returnStatus(Status.BadRequest)
    }

    "Not find blank asset" in {
      val req = Request[IO](uri = uri("/test-lib/1.0.0/"))
      routes.apply(req).value must returnValue(Option.empty[Response[IO]])
    }

    "Not match a request with POST" in {
      val req = Request[IO](POST, Uri(path = "/test-lib/1.0.0/testresource.txt"))
      routes.apply(req).value must returnValue(Option.empty[Response[IO]])
    }
  }
}
