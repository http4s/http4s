package org.http4s
package parserold

import org.http4s.parserold.QueryParser._
import org.scalatest.{Matchers, WordSpec}
import org.http4s.parser.ParseErrorInfo

class QueryParserSpec extends WordSpec with Matchers {

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
      parseQueryString("a=b=c") should equal (Left {
        ParseErrorInfo(
          "Illegal query string",
          "Invalid input '=', expected '&' or EOI (line 1, pos 4):\n" +
          "a=b=c\n" +
          "   ^\n"
        )
      })
    }
    "throw a proper HttpException on illegal URL encodings" in {
      parseQueryString("a=b%G") should equal (
        Left(ParseErrorInfo("Illegal query string", "URLDecoder: Incomplete trailing escape (%) pattern")))
    }
  }

}