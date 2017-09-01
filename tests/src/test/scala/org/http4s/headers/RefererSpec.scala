package org.http4s.headers

import cats.implicits._
import org.http4s.Uri

class RefererSpec extends HeaderLaws {
  checkAll("Referer", headerLaws(`Retry-After`))

  def getUri(uri: String): Uri =
    Uri.fromString(uri).fold(_ => sys.error(s"Failure on uri: $uri"), identity)

  "render" should {
    "format an absolute url" in {
      Referer(getUri("http://localhost:8080")).renderString must_== "Referer: http://localhost:8080"
    }
    "format a relative url" in {
      Referer(getUri("../../index.html")).renderString must_== "Referer: ../../index.html"
    }
  }

  "parse" should {
    "accept absolute url" in {
      Referer.parse("http://localhost:8080").map(_.uri) must beRight(
        getUri("http://localhost:8080"))
    }
    "accept relative url" in {
      Referer.parse("../../index.html").map(_.uri) must beRight(getUri("../../index.html"))
    }
  }
}
