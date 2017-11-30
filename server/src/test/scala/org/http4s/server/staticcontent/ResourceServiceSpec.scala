package org.http4s
package server
package staticcontent

import cats.effect._
import org.http4s.headers.{`Accept-Encoding`, `If-Modified-Since`}
import org.http4s.server.middleware.URITranslation
import Message.messSyntax._

class ResourceServiceSpec extends Http4sSpec with StaticContentShared {

  val config = ResourceService.Config[IO]("", executionContext = Http4sSpec.TestExecutionContext)
  val s = resourceService(config)

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

    "Try to serve pre-gzipped content if asked to" in {
      val req = Request[IO](
        uri = Uri.fromString("testresource.txt").yolo,
        headers = Headers(`Accept-Encoding`(ContentCoding.gzip))
      )
      val rb = resourceService(config.copy(preferGzipped = true)).orNotFound(req)

      rb must returnBody(testResourceGzipped)
      rb must returnStatus(Status.Ok)
      rb must returnValue(haveMediaType(MediaType.`text/plain`))
      rb must returnValue(haveContentCoding(ContentCoding.gzip))
    }

    "Fallback to un-gzipped file if pre-gzipped version doesn't exist" in {
      val req = Request[IO](
        uri = Uri.fromString("testresource2.txt").yolo,
        headers = Headers(`Accept-Encoding`(ContentCoding.gzip))
      )
      val rb = resourceService(config.copy(preferGzipped = true)).orNotFound(req)

      rb must returnBody(testResource)
      rb must returnStatus(Status.Ok)
      rb must returnValue(haveMediaType(MediaType.`text/plain`))
      rb must not(returnValue(haveContentCoding(ContentCoding.gzip)))
    }

    "Generate non on missing content" in {
      val req = Request[IO](uri = Uri.fromString("testresource.txtt").yolo)
      s.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Not send unmodified files" in {
      val req = Request[IO](uri = uri("testresource.txt"))
        .putHeaders(`If-Modified-Since`(HttpDate.MaxValue))

      runReq(req)._2.status must_== Status.NotModified
    }
  }
}
