package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}

class QueryParserSpec extends WordSpec with Matchers {

  def parseQueryString(str: String): Either[ParseErrorInfo, Seq[(String, Option[String])]] =
    QueryParser.parseQueryString(str)

  "The QueryParser" should {

    "correctly extract complete key value pairs" in {
      parseQueryString("key=value") should equal (Right(Seq("key" -> Some("value"))))
      parseQueryString("key=value&key2=value2") should equal (Right(Seq("key" -> Some("value"), "key2" -> Some("value2"))))
    }

    "decode URL-encoded keys and values" in {
      parseQueryString("ke%25y=value") should equal (Right(Seq("ke%y" -> Some("value"))))
      parseQueryString("key=value%26&key2=value2") should equal (Right(Seq("key" -> Some("value&"), "key2" -> Some("value2"))))
    }

    "return an empty Map for an empty query string" in {
      parseQueryString("") should equal (Right(Seq()))
    }

    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      parseQueryString("key=&key2") should equal (Right(Seq("key" -> Some(""), "key2" -> None)))
    }

    "accept empty key value pairs" in {
      parseQueryString("&&b&") should equal (Right(Seq("" -> None, "" -> None, "b" -> None, "" -> None)))
    }

    "Handle '=' in a query string" in {
      parseQueryString("a=b=c") should equal (Right(Seq("a" -> Some("b=c"))))
    }

    "Gracefully handle invalid URL encoding" in {
      parseQueryString("a=b%G") should equal (Right(Seq("a" -> Some("b%G"))))
    }
  }

}
