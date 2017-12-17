package org.http4s
package parser

import java.nio.CharBuffer
import scala.io.Codec

class QueryParserSpec extends Http4sSpec {

  def parseQueryString(str: String): ParseResult[Query] =
    QueryParser.parseQueryString(str)

  "The QueryParser" should {
    "correctly extract complete key value pairs" in {
      parseQueryString("key=value") must beRight(Query("key" -> Some("value")))
      parseQueryString("key=value&key2=value2") must beRight(
        Query("key" -> Some("value"), "key2" -> Some("value2")))
    }

    "decode URL-encoded keys and values" in {
      parseQueryString("ke%25y=value") must beRight(Query("ke%y" -> Some("value")))
      parseQueryString("key=value%26&key2=value2") must beRight(
        Query("key" -> Some("value&"), "key2" -> Some("value2")))
    }

    "return an empty Map for an empty query string" in {
      parseQueryString("") must beRight(Query.empty)
    }

    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      parseQueryString("key=&key2") must beRight(Query("key" -> Some(""), "key2" -> None))
    }

    "accept empty key value pairs" in {
      parseQueryString("&&b&") must beRight(Query("" -> None, "" -> None, "b" -> None, "" -> None))
    }

    "Handle '=' in a query string" in {
      parseQueryString("a=b=c") must beRight(Query("a" -> Some("b=c")))
    }

//    "Gracefully handle invalid URL encoding" in {
//      parseQueryString("a=b%G") must beRight(Query("a" -> Some("b%G")))
//    }

    "Allow ';' seperators" in {
      parseQueryString("a=b;c") must beRight(Query("a" -> Some("b"), "c" -> None))
    }

    "Allow PHP-style [] in keys" in {
      parseQueryString("a[]=b&a[]=c") must beRight(Query("a[]" -> Some("b"), "a[]" -> Some("c")))
    }

    "QueryParser using QChars doesn't allow PHP-style [] in keys" in {
      val queryString = "a[]=b&a[]=c"
      new QueryParser(Codec.UTF8, true, QueryParser.QChars)
        .decode(CharBuffer.wrap(queryString), true) must beLeft
    }

    "Reject a query with invalid char" in {
      parseQueryString("獾") must beLeft
      parseQueryString("foo獾bar") must beLeft
      parseQueryString("foo=獾") must beLeft
    }

    "Keep CharBuffer position if not flushing" in {
      val s = "key=value&stuff=cat"
      val cs = CharBuffer.wrap(s)
      val r = new QueryParser(Codec.UTF8, true).decode(cs, false)

      r must beRight(Query("key" -> Some("value")))
      cs.remaining must_== 9

      val r2 = new QueryParser(Codec.UTF8, true).decode(cs, false)
      r2 must beRight(Query())
      cs.remaining() must_== 9

      val r3 = new QueryParser(Codec.UTF8, true).decode(cs, true)
      r3 must beRight(Query("stuff" -> Some("cat")))
      cs.remaining() must_== 0
    }

    "be stack safe" in {
      val value = Stream.continually('X').take(1000000).mkString
      val query = s"little=x&big=${value}"
      parseQueryString(query) must beRight(Query("little" -> Some("x"), "big" -> Some(value)))
    }
  }
}
