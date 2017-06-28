package org.http4s
package server
package staticcontent

import org.http4s.Method.{GET, POST}
import org.http4s.server.staticcontent.WebjarService.Config
import org.http4s.server.staticcontent.WebjarServiceFilterSpec.{runReq, throwA}

object WebjarServiceSpec extends Http4sSpec with StaticContentShared {

  def s: HttpService = webjarService(Config())

  "The WebjarService" should {

    "Return a 200 Ok file" in {
      val req = Request(GET, Uri(path = "/test-lib/1.0.0/testresource.txt"))
      val rb = runReq(req)

      rb._1 must_== testWebjarResource
      rb._2.status must_== Status.Ok
    }

    "Return a 200 Ok file in a subdirectory" in {
      val req = Request(GET, Uri(path = "/test-lib/1.0.0/sub/testresource.txt"))
      val rb = runReq(req)

      rb._1 must_== testWebjarSubResource
      rb._2.status must_== Status.Ok
    }

    "Not find missing file" in {
      val req = Request(uri = uri("/test-lib/1.0.0/doesnotexist.txt"))
      s.apply(req) must returnValue(Pass)
    }

    "Not find missing library" in {
      val req = Request(uri = uri("/1.0.0/doesnotexist.txt"))
      s.apply(req) must returnValue(Pass)
    }

    "Not find missing version" in {
      val req = Request(uri = uri("/test-lib//doesnotexist.txt"))
      s.apply(req) must returnValue(Pass)
    }

    "Not find missing asset" in {
      val req = Request(uri = uri("/test-lib/1.0.0/"))
      s.apply(req) must returnValue(Pass)
    }

    "Not match a request with POST" in {
      val req = Request(POST, Uri(path = "/test-lib/1.0.0/testresource.txt"))
      s.apply(req) must returnValue(Pass)
    }
  }
}
