package org.http4s

import org.scalacheck.Arbitrary
import org.specs2.ScalaCheck
import org.specs2.scalacheck.Parameters

import scalaz.{NonEmptyList, \/-}

class UrlFormSpec extends Http4sSpec with ScalaCheck {
  // These tests are slow.  Let's lower the bar.
  implicit val params = Parameters(maxSize = 40)

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
    val charset = Charset.`UTF-8`

    "entityDecoder . entityEncoder == right" in prop{ (urlForm: UrlForm) =>
      UrlForm.entityDecoder.decode(
        Request().withBody(urlForm)(UrlForm.entityEncoder(charset)).run,
        strict = false
      ).run.run must_== \/-(urlForm)
    }

    "decodeString . encodeString == right" in prop{ (urlForm: UrlForm) =>
      UrlForm.decodeString(charset)(
        UrlForm.encodeString(charset)(urlForm)
      ) must_== \/-(urlForm)
    }

    "get returns elements matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).get("key") must_== Seq("a", "b", "c")
    }

    "get returns empty Seq if no matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).get("notFound") must_== Seq.empty[String]
    }

    "getFirst returns first element matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getFirst("key") must_== Some("a")
    }

    "getFirst returns None if no matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getFirst("notFound") must_== None
    }

    "getOrElse returns elements matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getOrElse("key", Seq("d")) must_== Seq("a", "b", "c")
    }

    "getOrElse returns default if no matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getOrElse("notFound", Seq("d")) must_== Seq("d")
    }

    "getFirstOrElse returns first element matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getFirstOrElse("key", "d") must_== "a"
    }

    "getFirstOrElse returns default if no matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getFirstOrElse("notFound", "d") must_== "d"
    }

    // Not quite sure why this is necessary, but the compiler gives us a diverging implicit if not present
    implicit val chooseArb: Arbitrary[NonEmptyList[String]] = scalaz.scalacheck.ScalazArbitrary.NonEmptyListArbitrary
    "construct consistently from kv-pairs or and Map[String, Seq[String]]" in prop {
      map: Map[String, NonEmptyList[String]] => // non-empty because the kv-constructor can't represent valueless fields
        val flattened = for {
          (k, vs) <- map.toSeq
          v <- vs.list
        } yield (k -> v)
        UrlForm(flattened: _*) must_== UrlForm(map.mapValues(_.list))
    }
  }

}
