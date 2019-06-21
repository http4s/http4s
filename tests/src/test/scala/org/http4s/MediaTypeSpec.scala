package org.http4s

import cats.kernel.laws.discipline.EqTests
import org.http4s.laws.discipline.HttpCodecTests
import cats.syntax.show._

class MediaTypeSpec extends Http4sSpec {

  "MediaType" should {

    "Render itself" in {
      MediaType.text.html.show must_== "text/html"
    }

    "Quote extension strings" in {
      MediaType.text.html
        .withExtensions(Map("foo" -> "bar"))
        .show must_== """text/html; foo="bar""""
    }

    "Encode extensions with special characters" in {
      MediaType.text.html
        .withExtensions(Map("foo" -> ";"))
        .show must_== """text/html; foo=";""""
    }

    "Escape special chars in media range extensions" in {
      MediaType.text.html
        .withExtensions(Map("foo" -> "\\"))
        .show must_== """text/html; foo="\\""""
      MediaType.text.html
        .withExtensions(Map("foo" -> "\""))
        .show must_== """text/html; foo="\"""""
    }

    "Do a round trip through the Accept header" in {
      val raw = Header(
        "Accept",
        """text/csv;charset=UTF-8;columnDelimiter=" "; rowDelimiter=";"; quoteChar='; escapeChar="\\"""")
      raw.parsed must beAnInstanceOf[headers.Accept]
      Header("Accept", raw.parsed.value).parsed must_== raw.parsed
    }

    "parse literals" in {
      val mediaType = MediaType.mediaType("application/json")
      val mediaTypeSC = mediaType"application/json"

      mediaType must_== MediaType.application.`json`
      mediaTypeSC must_== MediaType.application.`json`
    }

    "reject invalid literals" in {
      import org.specs2.execute._, Typecheck._
      import org.specs2.matcher.TypecheckMatchers._

      typecheck {
        """
           MediaType.mediaType("not valid")
        """
      } must not succeed

      typecheck {
        """
           mediaType"not valid"
        """
      } must not succeed
    }
  }

  checkAll("Eq[MediaType]", EqTests[MediaType].eqv)
  checkAll("HttpCodec[MediaType]", HttpCodecTests[MediaType].httpCodec)
}
