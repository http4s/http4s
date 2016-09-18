package org.http4s
package server
package staticcontent

import org.http4s.Http4sSpec._
import org.http4s.server.middleware.URITranslation

class ResourceServiceSpec extends Http4sSpec with StaticContentShared {

  val s = resourceService(ResourceService.Config("", executor = TestPool))

  "ResourceService" should {

    "Respect UriTranslation" in {
      val s2 = URITranslation.translateRoot("/foo")(s)

      {
        val req = Request(uri = uri("foo/testresource.txt"))
        s2(req) must returnBody(testResource)
        s2(req) must returnStatus(Status.Ok)
      }

      {
        val req = Request(uri = uri("testresource.txt"))
        s2(req) must returnStatus(Status.NotFound)
      }
    }

    "Serve available content" in {
      val req = Request(uri = Uri.fromString("testresource.txt").yolo)
      val rb = s(req)

      rb must returnBody(testResource)
      rb must returnStatus(Status.Ok)
    }

    "Generate non on missing content" in {
      val req = Request(uri = Uri.fromString("testresource.txtt").yolo)
      s(req) must returnStatus(Status.NotFound)
    }
  }

}
