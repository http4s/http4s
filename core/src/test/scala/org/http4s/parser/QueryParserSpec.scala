package org.http4s
package parser

import org.specs2.mutable.Specification
import org.http4s.parser.QueryParser._
import spray.http.RequestErrorInfo

class QueryParserSpec extends Specification {

  "The QueryParser" should {
    "correctly extract complete key value pairs" in {
      parseQueryString("key=value") === Right(Seq("key" -> "value"))
      parseQueryString("key=value&key2=value2") === Right(Seq("key" -> "value", "key2" -> "value2"))
    }
    "decode URL-encoded keys and values" in {
      parseQueryString("ke%25y=value") === Right(Seq("ke%y" -> "value"))
      parseQueryString("key=value%26&key2=value2") === Right(Seq("key" -> "value&", "key2" -> "value2"))
    }
    "return an empty Map for an empty query string" in {
      parseQueryString("") === Right(Seq())
    }
    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      parseQueryString("key=&key2") === Right(Seq("key" -> "", "key2" -> ""))
    }
    "accept empty key value pairs" in {
      parseQueryString("&&b&") === Right(Seq("" -> "", "" -> "", "b" -> "", "" -> ""))
    }
    "produce a proper error message on illegal query strings" in {
      parseQueryString("a=b=c") === Left {
        RequestErrorInfo(
          "Illegal query string",
          """|Invalid input '=', expected '&' or EOI (line 1, pos 4):
           |a=b=c
           |   ^
           |""".stripMargin
        )
      }
    }
    "throw a proper HttpException on illegal URL encodings" in {
      parseQueryString("a=b%G") ===
        Left(RequestErrorInfo("Illegal query string", "URLDecoder: Incomplete trailing escape (%) pattern"))
    }
  }

}