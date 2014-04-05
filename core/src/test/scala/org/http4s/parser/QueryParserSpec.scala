package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}

class QueryParserSpec extends WordSpec with Matchers {

  def parseQueryString(str: String): Either[ParseErrorInfo, Seq[(String, String)]] =
    QueryParser.parseQueryString(str)

  "The QueryParser" should {
    "correctly extract complete key value pairs" in {
      parseQueryString("key=value") should equal (Right(Seq("key" -> "value")))
      parseQueryString("key=value&key2=value2") should equal (Right(Seq("key" -> "value", "key2" -> "value2")))
    }
    "decode URL-encoded keys and values" in {
      parseQueryString("ke%25y=value") should equal (Right(Seq("ke%y" -> "value")))
      parseQueryString("key=value%26&key2=value2") should equal (Right(Seq("key" -> "value&", "key2" -> "value2")))
    }
    "return an empty Map for an empty query string" in {
      parseQueryString("") should equal (Right(Seq()))
    }
    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      parseQueryString("key=&key2") should equal (Right(Seq("key" -> "", "key2" -> "")))
    }
    "accept empty key value pairs" in {
      parseQueryString("&&b&") should equal (Right(Seq("" -> "", "" -> "", "b" -> "", "" -> "")))
    }
    "produce a proper error message on illegal query strings" in {
      parseQueryString("a=b=c").left.map(_.copy(detail = "")) should equal (
        Left(ParseErrorInfo("Illegal query string: 'a=b=c'", ""))
      )
    }
    "throw a proper HttpException on illegal URL encodings" in {

      println(parseQueryString("a=b%G").left.map(_.copy(detail = "")).left.get.detail)
      println(parseQueryString("a=b%G").left.map(_.copy(detail = "")).left.get.summary)
      parseQueryString("a=b%G") should equal (
        Left(ParseErrorInfo("Illegal query string", "URLDecoder: Incomplete trailing escape (%) pattern")))
    }
  }

}
