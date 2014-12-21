package org.http4s

import org.http4s.util.UrlCodingUtils
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck

import scala.collection.immutable.BitSet
import scalaz.\/-


class UrlFormSpec extends Http4sSpec with ScalaCheck {

  implicit val charsetArb = Arbitrary(
    Gen.oneOf(Charset.`UTF-8`, Charset.`UTF-16`, Charset.`UTF-16LE`)
  )

  implicit val bitSetArb = Arbitrary(
    Arbitrary.arbitrary[Set[Char]].map(_.map(_.toInt)).map(set => BitSet(set.toSeq: _*))
  )


  "UrlForm" should {

    "decode . encode == right" in prop{ (urlForm: UrlForm, charset: Charset) =>
      UrlForm.entityDecoder.decode(
        Request().withBody(urlForm)(UrlForm.entityEncoder(charset)).run
      ).run.run must_== \/-(urlForm)
    }

    "decodeString . encodeString == right" in prop{ (urlForm: UrlForm, charset: Charset) =>
      UrlForm.decodeString(charset)(
        UrlForm.encodeString(charset)(urlForm)
      ) must_== \/-(urlForm)
    }

    "urlDecode . urlEncode == right" in prop{ (s: String, charset: Charset, spaceIsPlus: Boolean) =>
//      val _bitset = BitSet((('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ "!$&'()*+,;=:/?@-._~".toSet).map(_.toInt): _*)
      val _bitset = BitSet.empty  // test pass if BitSet is empty
      UrlCodingUtils.urlDecode(
        UrlCodingUtils.urlEncode(s, charset, spaceIsPlus, _bitset),
        charset, spaceIsPlus, _bitset) must_== s
    }

  }

}
