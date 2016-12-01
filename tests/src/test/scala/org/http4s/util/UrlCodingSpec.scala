/**
  * Taken from https://github.com/scalatra/rl/blob/v0.4.10/core/src/test/scala/rl/UrlCodingSpec.scala
  * Copyright (c) 2011 Mojolly Ltd.
  */
package org.http4s
package util

import org.http4s.util.UrlCodingUtils._
import org.parboiled2.CharPredicate

class UrlCodingSpec extends Http4sSpec {
  "Encoding a URI" should {
    "not change any of the allowed chars" in {
      val encoded = urlEncode("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*+,;=:/?@-._~")
      encoded must_== "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*+,;=:/?@-._~"
    }
    "not uppercase hex digits after percent chars that will be encoded" in {
      // https://github.com/http4s/http4s/issues/720
      urlEncode("hello%3fworld") must_== "hello%253fworld"
    }
    "percent encode spaces" in {
      urlEncode("hello world") must_== "hello%20world"
    }
    "encode a letter with an accent as 2 values" in {
      urlEncode("é") must_== "%C3%A9"
    }
  }
  "Decoding a URI" should {
    "not change any of the allowed chars" in {
      val decoded = urlDecode("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*,;=:/?#[]@-._~")
      decoded must_== "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!$&'()*,;=:/?#[]@-._~"
    }
    "leave Fußgängerübergänge as is" in {
      urlDecode("Fußgängerübergänge") must_== "Fußgängerübergänge"
    }
    "not overflow on all utf-8 chars" in {
      urlDecode("äéèüああああああああ") must_== "äéèüああああああああ"
    }
    "decode a pct encoded string" in {
      urlDecode("hello%20world") must_== "hello world"
    }
    "gracefully handle '%' encoding errors" in {
      urlDecode("%") must_== "%"
      urlDecode("%2") must_== "%2"
      urlDecode("%20") must_== " "
    }
    "decode value consisting of 2 values to 1 char" in {
      urlDecode("%C3%A9") must_== "é"
    }
    "skip the chars in toSkip when decoding" in {
      "skips '%2F' when decoding" in { urlDecode("%2F", toSkip = CharPredicate("/?#")) must_== "%2F" }
      "skips '%23' when decoding" in { urlDecode("%23", toSkip = CharPredicate("/?#")) must_== "%23" }
      "skips '%3F' when decoding" in { urlDecode("%3F", toSkip = CharPredicate("/?#")) must_== "%3F" }
    }
    "still encodes others" in { urlDecode("br%C3%BCcke", toSkip = CharPredicate("/?#")) must_== "brücke"}
    "handles mixed" in { urlDecode("/ac%2Fdc/br%C3%BCcke%2342%3Fcheck", toSkip = CharPredicate("/?#")) must_== "/ac%2Fdc/brücke%2342%3Fcheck"}
  }
  "The plusIsSpace flag" should {
    "treats + as allowed when the plusIsSpace flag is either not supplied or supplied as false" in {
      urlDecode("+") must_== "+"
      urlDecode("+", plusIsSpace = false) must_== "+"
    }
    "decode + as space when the plusIsSpace flag is true" in {
      urlDecode("+", plusIsSpace = true) must_== " "
    }
  }

  "urlDecode(urlEncode(s)) == s" should {
    "for all s" in prop { (s: String) =>
      urlDecode(urlEncode(s)) must_== s
    }
    """for "%ab"""" in {
      // Special case that triggers https://github.com/http4s/http4s/issues/720,
      // not likely to be uncovered by the generator.
      urlDecode(urlEncode("%ab")) must_== "%ab"
    }
    """when decode skips a skipped percent encoding""" in {
      // This is a silly thing to do, but as long as the API allows it, it would
      // be good to know if it breaks.
      urlDecode(urlEncode("%2f", toSkip = CharPredicate("%")), toSkip = CharPredicate("/")) must_== "%2f"
    }    
  }
}
