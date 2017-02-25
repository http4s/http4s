package org.http4s.server.staticcontent


import org.http4s.Method.{GET, POST}
import org.http4s._
import org.http4s.server.staticcontent.WebjarService.Config

object WebjarServiceFilterSpec extends Http4sSpec with StaticContentShared {

  def s: HttpService = webjarService(
    Config(pathPrefix = "prefix", filter = (webjar) =>
      webjar.library == "test-lib" && webjar.version == "1.0.0" && webjar.asset == "testresource.txt"
    )
  )

  "The WebjarService" should {

    "Return a 200 Ok file" in {
      val req = Request(GET, Uri(path = "prefix/test-lib/1.0.0/testresource.txt"))
      val rb = runReq(req)

      rb._1 must_== testWebjarResource
      rb._2.status must_== Status.Ok
    }

    "Not find filtered asset" in {
      val req = Request(GET, Uri(path = "prefix/test-lib/1.0.0/sub/testresource.txt"))
      val rb = runReq(req)

      rb._2.status must_== Status.NotFound
    }

    "Not find asset with without prefix" in {
      val req = Request(GET, Uri(path = "test-lib/1.0.0/testresource.txt"))
      val rb = runReq(req)

      rb._2.status must_== Status.NotFound
    }

    "Not find asset with with wrong prefix" in {
      val req = Request(GET, Uri(path = "foobar/test-lib/1.0.0/testresource.txt"))
      val rb = runReq(req)

      rb._2.status must_== Status.NotFound
    }

    "Not find an asset with prefix, but without slash" in {
      val req = Request(GET, Uri(path = "prefixtest-lib/1.0.0/testresource.txt"))
      val rb = runReq(req)

      rb._2.status must_== Status.NotFound
    }

    "Not match a request with POST" in {
      val req = Request(POST, Uri(path = "prefix/test-lib/1.0.0/testresource.txt"))

      runReq(req) must throwA[MatchError]
    }

  }

}
