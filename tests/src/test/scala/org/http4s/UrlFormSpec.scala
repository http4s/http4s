package org.http4s

import cats.Monoid
import cats.data._
import cats.effect.IO
import cats.implicits._
import cats.kernel.laws.discipline.MonoidTests

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
        .success(
          Request[IO]()
            .withEntity(urlForm)(UrlForm.entityEncoder(charset))
            .pure[IO])
        .flatMap { req =>
          UrlForm.entityDecoder[IO].decode(req, strict = false)
        }
        .value
        .unsafeRunSync() === Right(urlForm)
    }

    "decodeString . encodeString == right" in prop { (urlForm: UrlForm) =>
      UrlForm
        .decodeString(charset)(
          UrlForm.encodeString(charset)(urlForm)
        )
        .leftWiden[DecodeFailure] === Right(urlForm)
    }

    "get returns elements matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c"))).get("key") must_== Chain("a", "b", "c")
    }

    "get returns empty Chain if no matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c"))).get("notFound") must_== Chain.empty[String]
    }

    "getFirst returns first element matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirst("key") must beSome("a")
    }

    "getFirst returns None if no matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirst("notFound") must beNone
    }

    "getOrElse returns elements matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c")))
        .getOrElse("key", Chain("d")) must_== Chain("a", "b", "c")
    }

    "getOrElse returns default if no matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c"))).getOrElse("notFound", Chain("d")) must_== Chain(
        "d")
    }

    "getFirstOrElse returns first element matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirstOrElse("key", "d") must_== "a"
    }

    "getFirstOrElse returns default if no matching key" in {
      UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirstOrElse("notFound", "d") must_== "d"
    }

    "withFormField encodes T properly if QueryParamEncoder[T] can be resolved" in {
      UrlForm.empty.updateFormField("foo", 1).get("foo") must_== Chain("1")
      UrlForm.empty.updateFormField("bar", Some(true)).get("bar") must_== Chain("true")
      UrlForm.empty.updateFormField("bar", Option.empty[Boolean]).get("bar") must_== Chain()
      UrlForm.empty.updateFormFields("dummy", Chain("a", "b", "c")).get("dummy") === Chain(
        "a",
        "b",
        "c")
    }

    "withFormField is effectively equal to factory constructor that takes a Map" in {
      (
        UrlForm.empty +? ("foo", 1) +? ("bar", Some(true)) ++? ("dummy", Chain("a", "b", "c")) ===
          UrlForm(Map("foo" -> Chain("1"), "bar" -> Chain("true"), "dummy" -> Chain("a", "b", "c")))
      )

      (
        UrlForm.empty +? ("foo", 1) +? ("bar", Option
          .empty[Boolean]) ++? ("dummy", Chain("a", "b", "c")) ===
          UrlForm(Map("foo" -> Chain("1"), "dummy" -> Chain("a", "b", "c")))
      )
    }

    "construct consistently from kv-pairs or and Map[String, Chain[String]]" in prop {
      map: Map[String, NonEmptyList[String]] => // non-empty because the kv-constructor can't represent valueless fields
        val flattened = for {
          (k, vs) <- map.toSeq
          v <- vs.toList
        } yield k -> v
        UrlForm(flattened: _*) === UrlForm(map.mapValues(nel => Chain.fromSeq(nel.toList)))
    }

    "construct consistently from Chain of kv-pairs and Map[String, Chain[String]]" in prop {
      map: Map[String, NonEmptyList[String]] => // non-empty because the kv-constructor can't represent valueless fields
        val flattened = for {
          (k, vs) <- Chain.fromSeq(map.toSeq)
          v <- Chain.fromSeq(vs.toList)
        } yield k -> v
        UrlForm.fromChain(flattened) === UrlForm(map.mapValues(nel => Chain.fromSeq(nel.toList)))
    }
  }

  checkAll("monoid", MonoidTests[UrlForm].monoid)

  "UrlForm monoid" should {
    "use the obvious empty" in {
      Monoid[UrlForm].empty must_== UrlForm.empty
    }

    "combine two UrlForm instances" in {
      val combined = UrlForm("foo" -> "1") |+| UrlForm("bar" -> "2", "foo" -> "3")
      combined must_== UrlForm("foo" -> "1", "bar" -> "2", "foo" -> "3")
    }
  }
}
