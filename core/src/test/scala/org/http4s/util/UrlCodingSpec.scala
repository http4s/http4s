///**
// * Taken from https://github.com/scalatra/rl/blob/v0.4.10/core/src/test/scala/rl/UrlCodingSpec.scala
// * Copyright (c) 2011 Mojolly Ltd.
// */
//package org.http4s.util
//
//import org.http4s.util.encoding.UriCodingUtils._
//import org.specs2.{ScalaCheck, Specification}
//
//import scala.collection.immutable.BitSet
//
//class UrlCodingSpec extends Specification with ScalaCheck {
//  def bitSet(s: String): BitSet = BitSet(s.toSet[Char].map(_.toInt).toSeq: _*)
//
//  def is =
//
//    "Encoding a URI should" ^
//      "not change any of the allowed chars" ! {
//        val encoded = urlEncode("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*+,;=:/?@-._~")
//        encoded must_== "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*+,;=:/?@-._~"
//      } ^
//      "uppercase encodings already in a string" ! {
//        ensureUppercasedEncodings("hello%3fworld") must_== "hello%3Fworld"
//      } ^
//      "percent encode spaces" ! {
//        urlEncode("hello world") must_== "hello%20world"
//      } ^
//      "encode a letter with an accent as 2 values" ! {
//        urlEncode("é") must_== "%C3%A9"
//      } ^ p ^
//    "Decoding a URI should" ^
//      "not change any of the allowed chars" ! {
//        val decoded = urlDecode("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*,;=:/?#[]@-._~")
//        decoded must_== "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*,;=:/?#[]@-._~"
//      } ^
//      "leave Fußgängerübergänge as is" ! {
//        urlDecode("Fußgängerübergänge") must_== "Fußgängerübergänge"
//      } ^
//      "not overflow on all utf-8 chars" ! {
//        urlDecode("äéèüああああああああ") must_== "äéèüああああああああ"
//      } ^
//      "decode a pct encoded string" ! {
//        urlDecode("hello%20world") must_== "hello world"
//      } ^
//      "gracefully handle '%' encoding errors" ! {
//        urlDecode("%") must_== "%"
//        urlDecode("%2") must_== "%2"
//        urlDecode("%20") must_== " "
//      } ^
//      "decode value consisting of 2 values to 1 char" ! {
//        urlDecode("%C3%A9") must_== "é"
//      } ^
//      "skip the chars in toSkip when decoding" ^
//        "skips '%2F' when decoding" ! { urlDecode("%2F", toSkip = bitSet("/?#")) must_== "%2F" } ^
//        "skips '%23' when decoding" ! { urlDecode("%23", toSkip = bitSet("/?#")) must_== "%23" } ^
//        "skips '%3F' when decoding" ! { urlDecode("%3F", toSkip = bitSet("/?#")) must_== "%3F" } ^
//        "still encodes others" ! { urlDecode("br%C3%BCcke", toSkip = bitSet("/?#")) must_== "brücke"} ^
//        "handles mixed" ! { urlDecode("/ac%2Fdc/br%C3%BCcke%2342%3Fcheck", toSkip = bitSet("/?#")) must_== "/ac%2Fdc/brücke%2342%3Fcheck"} ^ p ^
//    "The plusIsSpace flag specifies how to treat pluses" ^
//      "it treats + as allowed when the plusIsSpace flag is either not supplied or supplied as false" ! {
//        urlDecode("+") must_== "+"
//        urlDecode("+", plusIsSpace = false) must_== "+"
//      } ^
//      "it decodes + as space when the plusIsSpace flag is true" ! {
//        urlDecode("+", plusIsSpace = true) must_== " "
//      } ^ p ^
//    "urlDecode(urlEncode(s)) == s" ! {
//      prop { (s: String) => urlDecode(urlEncode(s)) must_== s }
//    } ^ end
//}
//
//
