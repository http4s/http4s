package org.http4s

import cats._
import cats.data._
import cats.effect.IO

class UrlFormSpec extends Http4sSpec {
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

    "entityDecoder . entityEncoder == right" in prop { (urlForm: UrlForm) =>
      DecodeResult
        .success(Request[IO]()
          .withBody(urlForm)(Monad[IO], UrlForm.entityEncoder(Applicative[IO], charset)))
        .flatMap { req =>
          UrlForm.entityDecoder[IO].decode(req, strict = false)
        } must returnRight(urlForm)
    }

    "decodeString . encodeString == right" in prop { (urlForm: UrlForm) =>
      UrlForm.decodeString(charset)(
        UrlForm.encodeString(charset)(urlForm)
      ) must beRight(urlForm)
    }

    "get returns elements matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).get("key") must_== Seq("a", "b", "c")
    }

    "get returns empty Seq if no matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).get("notFound") must_== Seq.empty[String]
    }

    "getFirst returns first element matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getFirst("key") must beSome("a")
    }

    "getFirst returns None if no matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c"))).getFirst("notFound") must beNone
    }

    "getOrElse returns elements matching key" in {
      UrlForm(Map("key" -> Seq("a", "b", "c")))
        .getOrElse("key", Seq("d")) must_== Seq("a", "b", "c")
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

    "withFormField encodes T properly if QueryParamEncoder[T] can be resolved" in {
      UrlForm.empty.updateFormField("foo", 1).get("foo") must_== Seq("1")
      UrlForm.empty.updateFormField("bar", Some(true)).get("bar") must_== Seq("true")
      UrlForm.empty.updateFormField("bar", Option.empty[Boolean]).get("bar") must_== Seq()
      UrlForm.empty.updateFormFields("dummy", List("a", "b", "c")).get("dummy") must_== Seq(
        "a",
        "b",
        "c")
    }

    "withFormField is effectively equal to factory constructor that takes a Map" in {
      (
        UrlForm.empty +? ("foo", 1) +? ("bar", Some(true)) ++? ("dummy", List("a", "b", "c")) ===
          UrlForm(Map("foo" -> Seq("1"), "bar" -> Seq("true"), "dummy" -> List("a", "b", "c")))
      ) must_== (true)

      (
        UrlForm.empty +? ("foo", 1) +? ("bar", Option
          .empty[Boolean]) ++? ("dummy", List("a", "b", "c")) ===
          UrlForm(Map("foo" -> Seq("1"), "dummy" -> List("a", "b", "c")))
      ) must_== (true)
    }

    "construct consistently from kv-pairs or and Map[String, Seq[String]]" in prop {
      map: Map[String, NonEmptyList[String]] => // non-empty because the kv-constructor can't represent valueless fields
        val flattened = for {
          (k, vs) <- map.toSeq
          v <- vs.toList
        } yield k -> v
        UrlForm(flattened: _*) must_== UrlForm(map.mapValues(_.toList))
    }

    "construct consistently from Seq of kv-pairs and Map[String, Seq[String]]" in prop {
      map: Map[String, NonEmptyList[String]] => // non-empty because the kv-constructor can't represent valueless fields
        val flattened = for {
          (k, vs) <- map.toSeq
          v <- vs.toList
        } yield (k -> v)
        UrlForm.fromSeq(flattened) must_== UrlForm(map.mapValues(_.toList))
    }
  }
}
