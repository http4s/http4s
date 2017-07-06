package org.http4s
package server
package staticcontent

import java.time.Instant

import cats.effect.IO
import org.http4s.headers.`If-Modified-Since`
import org.http4s.server.middleware.URITranslation

class ResourceServiceSpec extends Http4sSpec with StaticContentShared {

  val s = resourceService(ResourceService.Config[IO]("", executionContext = Http4sSpec.TestExecutionContext))

  "ResourceService" should {

    "Respect UriTranslation" in {
      val s2 = URITranslation.translateRoot("/foo")(s)

      {
        val req = Request[IO](uri = uri("foo/testresource.txt"))
        s2.orNotFound(req) must returnBody(testResource)
        s2.orNotFound(req) must returnStatus(Status.Ok)
      }

      {
        val req = Request[IO](uri = uri("testresource.txt"))
        s2.orNotFound(req) must returnStatus(Status.NotFound)
      }
    }

    "Serve available content" in {
      val req = Request[IO](uri = Uri.fromString("testresource.txt").yolo)
      val rb = s.orNotFound(req)

      rb must returnBody(testResource)
      rb must returnStatus(Status.Ok)
    }

    "Generate non on missing content" in {
      val req = Request[IO](uri = Uri.fromString("testresource.txtt").yolo)
      s.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Not send unmodified files" in {
      val req = Request[IO](uri = uri("testresource.txt"))
        .putHeaders(`If-Modified-Since`(Instant.MAX))

      runReq(req)._2.status must_== Status.NotModified
    }
  }
}
