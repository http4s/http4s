package org.http4s.parser

import org.specs2.mutable.Specification

class QueryParserSpec extends Specification {

  def parseQueryString(str: String): Either[ParseErrorInfo, Seq[(String, Option[String])]] =
    QueryParser.parseQueryString(str)

  "The QueryParser" should {

    "correctly extract complete key value pairs" in {
      parseQueryString("key=value") must be_== (Right(Seq("key" -> Some("value"))))
      parseQueryString("key=value&key2=value2") must be_== (Right(Seq("key" -> Some("value"), "key2" -> Some("value2"))))
    }

    "decode URL-encoded keys and values" in {
      parseQueryString("ke%25y=value") must be_== (Right(Seq("ke%y" -> Some("value"))))
      parseQueryString("key=value%26&key2=value2") must be_== (Right(Seq("key" -> Some("value&"), "key2" -> Some("value2"))))
    }

    "return an empty Map for an empty query string" in {
      parseQueryString("") must be_== (Right(Seq()))
    }

    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      parseQueryString("key=&key2") must be_== (Right(Seq("key" -> Some(""), "key2" -> None)))
    }

    "accept empty key value pairs" in {
      parseQueryString("&&b&") must be_== (Right(Seq("" -> None, "" -> None, "b" -> None, "" -> None)))
    }

    "Handle '=' in a query string" in {
      parseQueryString("a=b=c") must be_== (Right(Seq("a" -> Some("b=c"))))
    }

    "Gracefully handle invalid URL encoding" in {
      parseQueryString("a=b%G") must be_== (Right(Seq("a" -> Some("b%G"))))
    }
  }

}
