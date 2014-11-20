package org.http4s

import org.specs2.mutable.Specification

class UriSpec extends Specification {

  "Uri Query decoding" should {

    def getQueryParams(uri: String): Map[String, String] =
      Uri.fromString(uri).map(_.params).getOrElse(sys.error(s"Failure on uri: $uri"))

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
