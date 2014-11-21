package org.http4s

import org.specs2.mutable.Specification
import util.CaseInsensitiveString._
import org.http4s.Uri._

// TODO: this needs some more filling out
class UriSpec extends Specification {

  def getUri(uri: String): Uri =
    Uri.fromString(uri).fold(_ => sys.error(s"Failure on uri: $uri"), identity)

  "Uri" should {
    "Not UrlDecode the query String" in {
      getUri("http://localhost:8080/blah?x=abc&y=ijk").query should_== Some("x=abc&y=ijk")
    }

    "Not UrlDecode the uri fragment" in {
      getUri("http://localhost:8080/blah#x=abc&y=ijk").fragment should_== Some("x=abc&y=ijk")
    }

    "decode the scheme" in {
      val uri = getUri("http://localhost/")
      uri.scheme should_== Some("http".ci)
    }

    "decode the authority" in {
      val uri1 = getUri("http://localhost/")
      uri1.authority.get.host should_== RegName("localhost")

      val uri2 = getUri("http://localhost")
      uri2.authority.get.host should_== RegName("localhost")

      val uri3 = getUri("/foo/bar")
      uri3.authority should_== None

      val auth = getUri("http://localhost:8080/").authority.get
      auth.host should_== RegName("localhost")
      auth.port should_== Some(8080)
    }

    "decode the port" in {
      val uri1 = getUri("http://localhost:8080/")
      uri1.port should_== Some(8080)

      val uri2 = getUri("http://localhost/")
      uri2.port should_== None
    }
  }

  "Uri's with a query and fragment" should {
    "parse propperly" in {
      val uri = getUri("http://localhost:8080/blah?x=abc#y=ijk")
      uri.query should_== Some("x=abc")
      uri.fragment should_== Some("y=ijk")
    }
  }

  "Uri Query decoding" should {

    def getQueryParams(uri: String): Map[String, String] = getUri(uri).params

    "Handle queries with no spaces properly" in {
      getQueryParams("http://localhost:8080/blah?x=abc&y=ijk") should_== Map("x" -> "abc", "y" -> "ijk")
      getQueryParams("http://localhost:8080/blah?") should_== Map.empty
    }

    "Handle queries with spaces properly" in {
      // Issue #75
      getQueryParams("http://localhost:8080/blah?x=a+bc&y=ijk") should_== Map("x" -> "a bc", "y" -> "ijk")
      getQueryParams("http://localhost:8080/blah?x=a%20bc&y=ijk") should_== Map("x" -> "a bc", "y" -> "ijk")
    }

  }
}
