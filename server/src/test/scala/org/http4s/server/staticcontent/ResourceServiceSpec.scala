package org.http4s
package server
package staticcontent

import cats.effect._
import org.http4s.headers.{`Accept-Encoding`, `If-Modified-Since`}
import org.http4s.server.middleware.TranslateUri
import org.http4s.Uri.uri

class ResourceServiceSpec extends Http4sSpec with StaticContentShared {

  val config =
    ResourceService.Config[IO]("", blockingExecutionContext = testBlockingExecutionContext)
  val routes = resourceService(config)

  "ResourceService" should {

    "Respect UriTranslation" in {
      val app = TranslateUri("/foo")(routes).orNotFound

      {
        val req = Request[IO](uri = uri("foo/testresource.txt"))
        app(req) must returnBody(testResource)
        app(req) must returnStatus(Status.Ok)
      }

      {
        val req = Request[IO](uri = uri("testresource.txt"))
        app(req) must returnStatus(Status.NotFound)
      }
    }

    "Serve available content" in {
      val req = Request[IO](uri = Uri.fromString("testresource.txt").yolo)
      val rb = routes.orNotFound(req)

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
      rb must returnValue(haveMediaType(MediaType.text.plain))
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
      rb must returnValue(haveMediaType(MediaType.text.plain))
      rb must not(returnValue(haveContentCoding(ContentCoding.gzip)))
    }

    "Generate non on missing content" in {
      val req = Request[IO](uri = Uri.fromString("testresource.txtt").yolo)
      routes.orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Not send unmodified files" in {
      val req = Request[IO](uri = uri("testresource.txt"))
        .putHeaders(`If-Modified-Since`(HttpDate.MaxValue))

      runReq(req)._2.status must_== Status.NotModified
    }
  }
}
