package org.http4s
package parser

import org.specs2.mutable.Specification

class QueryParserSpec extends Http4sSpec {

  def parseQueryString(str: String): ParseResult[Seq[(String, Option[String])]] =
    QueryParser.parseQueryString(str)

  "The QueryParser" should {

    "correctly extract complete key value pairs" in {
      parseQueryString("key=value") must beRightDisjunction(Seq("key" -> Some("value")))
      parseQueryString("key=value&key2=value2") must beRightDisjunction(Seq("key" -> Some("value"), "key2" -> Some("value2")))
    }

    "decode URL-encoded keys and values" in {
      parseQueryString("ke%25y=value") must beRightDisjunction(Seq("ke%y" -> Some("value")))
      parseQueryString("key=value%26&key2=value2") must beRightDisjunction(Seq("key" -> Some("value&"), "key2" -> Some("value2")))
    }

    "return an empty Map for an empty query string" in {
      parseQueryString("") must beRightDisjunction(Seq.empty)
    }

    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      parseQueryString("key=&key2") must beRightDisjunction(Seq("key" -> Some(""), "key2" -> None))
    }

    "accept empty key value pairs" in {
      parseQueryString("&&b&") must beRightDisjunction(Seq("" -> None, "" -> None, "b" -> None, "" -> None))
    }

    "Handle '=' in a query string" in {
      parseQueryString("a=b=c") must beRightDisjunction(Seq("a" -> Some("b=c")))
    }

    "Gracefully handle invalid URL encoding" in {
      parseQueryString("a=b%G") must beRightDisjunction(Seq("a" -> Some("b%G")))
    }
  }

}
