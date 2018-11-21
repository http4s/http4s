package org.http4s.server.middleware

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.Uri.uri
import org.http4s.util.CaseInsensitiveString

class HeaderEchoSpec extends Http4sSpec {

  object someHeaderKey extends HeaderKey.Default
  object anotherHeaderKey extends HeaderKey.Default

  val testService = HttpRoutes.of[IO] {
    case GET -> Root / "request" => Ok("request response")
  }

  "HeaderEcho" should {
    "echo a single header in addition to the defaults" in {
      val requestMatchingSingleHeaderKey =
        Request[IO](
          uri = uri("/request"),
          headers = Headers(Header("someheaderkey", "someheadervalue")))
      val testee = HeaderEcho(_ == CaseInsensitiveString("someheaderkey"))(testService)
      val responseHeaders =
        testee.orNotFound(requestMatchingSingleHeaderKey).unsafeRunSync().headers

      responseHeaders.exists(_.value == "someheadervalue") must_== true
      (responseHeaders must have).size(3)
    }

    "echo multiple headers" in {
      val requestMatchingMultipleHeaderKeys =
        Request[IO](
          uri = uri("/request"),
          headers = Headers(
            Header("someheaderkey", "someheadervalue"),
            Header("anotherheaderkey", "anotherheadervalue")))
      val headersToEcho =
        List(CaseInsensitiveString("someheaderkey"), CaseInsensitiveString("anotherheaderkey"))
      val testee = HeaderEcho(headersToEcho.contains(_))(testService)

      val responseHeaders =
        testee.orNotFound(requestMatchingMultipleHeaderKeys).unsafeRunSync().headers

      responseHeaders.exists(_.value == "someheadervalue") must_== true
      responseHeaders.exists(_.value == "anotherheadervalue") must_== true
      (responseHeaders must have).size(4)
    }

    "echo only the default headers where none match the key" in {
      val requestMatchingNotPresentHeaderKey =
        Request[IO](
          uri = uri("/request"),
          headers = Headers(Header("someunmatchedheader", "someunmatchedvalue")))

      val testee = HeaderEcho(_ == CaseInsensitiveString("someheaderkey"))(testService)
      val responseHeaders =
        testee.orNotFound(requestMatchingNotPresentHeaderKey).unsafeRunSync().headers

      responseHeaders.exists(_.value == "someunmatchedvalue") must_== false
      (responseHeaders must have).size(2)
    }
  }

}
