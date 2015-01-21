package org.http4s

import org.http4s.util.UrlCodingUtils
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.matcher.Parameters

import scala.collection.immutable.BitSet
import scalaz.\/-


class UrlFormSpec extends Http4sSpec with ScalaCheck {
  // These tests are slow.  Let's lower the bar.
  implicit val p = Parameters(maxSize = 40)

//  // TODO: arbitrary charsets would be nice
//  /*
//   * Generating arbitrary Strings valid in an arbitrary Charset is an expensive operation.
//   * We'll sample a few incompatible, common charsets for which we know we're generating
//   * valid Strings.
//   */
//  implicit val charsetArb = Arbitrary(
//    Gen.oneOf(Charset.`UTF-8`, Charset.`UTF-16`, Charset.`UTF-16LE`)
//  )

  "UrlForm" should {
    val charset = Charset.`UTF-16`

    "entityDecoder . entityEncoder == right" in prop{ (urlForm: UrlForm) =>
      UrlForm.entityDecoder.decode(
        Request().withBody(urlForm)(UrlForm.entityEncoder(charset)).run
      ).run.run must_== \/-(urlForm)
    }

    "decodeString . encodeString == right" in prop{ (urlForm: UrlForm) =>
      UrlForm.decodeString(charset)(
        UrlForm.encodeString(charset)(urlForm)
      ) must_== \/-(urlForm)
    }


    "urlDecode . urlEncode == right" in prop{ (s: String, spaceIsPlus: Boolean, toSkip: BitSet) =>
      UrlCodingUtils.urlDecode(
        UrlCodingUtils.urlEncode(s, charset, spaceIsPlus, toSkip),
        charset, spaceIsPlus, toSkip) must_== s
    }

  }

}
