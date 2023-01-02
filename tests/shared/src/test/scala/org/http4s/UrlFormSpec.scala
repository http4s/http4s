/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.Monoid
import cats.data._
import cats.effect.IO
import cats.kernel.laws.discipline.MonoidTests
import cats.syntax.all.{catsSyntaxEq => _, _}
import org.http4s.internal.CollectionCompat
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop
import org.scalacheck.Test
import org.scalacheck.effect.PropF

class UrlFormSpec extends Http4sSuite {
//  // TODO: arbitrary charsets would be nice
//  /*
//   * Generating arbitrary Strings valid in an arbitrary Charset is an expensive operation.
//   * We'll sample a few incompatible, common charsets for which we know we're generating
//   * valid Strings.
//   */
//  implicit val charsetArb = Arbitrary(
//    Gen.oneOf(Charset.`UTF-8`, Charset.`UTF-16`, Charset.`UTF-16LE`)
//  )

  override def scalaCheckTestParameters: Test.Parameters =
    super.scalaCheckTestParameters.withMaxSize(20)

  {
    val charset = Charset.`UTF-8`

    test("UrlForm should entityDecoder . entityEncoder == right") {
      PropF.forAllF { (urlForm: UrlForm) =>
        DecodeResult
          .success(
            Request[IO]()
              .withEntity(urlForm)(UrlForm.entityEncoder(charset))
              .pure[IO]
          )
          .flatMap { req =>
            UrlForm.entityDecoder[IO].decode(req, strict = false)
          }
          .value
          .assertEquals(Right(urlForm))
      }
    }

    test("UrlForm should decodeString . encodeString == right") {
      Prop.forAll { (urlForm: UrlForm) =>
        UrlForm
          .decodeString(charset)(
            UrlForm.encodeString(charset)(urlForm)
          )
          .leftWiden[DecodeFailure] === Right(urlForm)
      }
    }

    test("UrlForm should get returns elements matching key") {
      assertEquals(UrlForm(Map("key" -> Chain("a", "b", "c"))).get("key"), Chain("a", "b", "c"))
    }

    test("UrlForm should get returns empty Chain if no matching key") {
      assertEquals(UrlForm(Map("key" -> Chain("a", "b", "c"))).get("notFound"), Chain.empty[String])
    }

    test("UrlForm should getFirst returns first element matching key") {
      assertEquals(UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirst("key"), Some("a"))
    }

    test("UrlForm should getFirst returns None if no matching key") {
      assertEquals(UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirst("notFound"), None)
    }

    test("UrlForm should getOrElse returns elements matching key") {
      assertEquals(
        UrlForm(Map("key" -> Chain("a", "b", "c")))
          .getOrElse("key", Chain("d")),
        Chain("a", "b", "c"),
      )
    }

    test("UrlForm should getOrElse returns default if no matching key") {
      assertEquals(
        UrlForm(Map("key" -> Chain("a", "b", "c"))).getOrElse("notFound", Chain("d")),
        Chain("d"),
      )
    }

    test("UrlForm should getFirstOrElse returns first element matching key") {
      assertEquals(UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirstOrElse("key", "d"), "a")
    }

    test("UrlForm should getFirstOrElse returns default if no matching key") {
      assertEquals(UrlForm(Map("key" -> Chain("a", "b", "c"))).getFirstOrElse("notFound", "d"), "d")
    }

    test(
      "UrlForm should withFormField encodes T properly if QueryParamEncoder[T] can be resolved"
    ) {
      assertEquals(UrlForm.empty.updateFormField("foo", 1).get("foo"), Chain("1"))
      assertEquals(UrlForm.empty.updateFormField("bar", Some(true)).get("bar"), Chain("true"))
      assertEquals(UrlForm.empty.updateFormField("bar", Option.empty[Boolean]).get("bar"), Chain())
      assertEquals(
        UrlForm.empty.updateFormFields("dummy", Chain("a", "b", "c")).get("dummy"),
        Chain("a", "b", "c"),
      )
    }

    test(
      "UrlForm should withFormField is effectively equal to factory constructor that takes a Map"
    ) {
      assertEquals(
        UrlForm.empty.+?("foo", 1).+?("bar", Some(true)).++?("dummy", Chain("a", "b", "c")),
        UrlForm(Map("foo" -> Chain("1"), "bar" -> Chain("true"), "dummy" -> Chain("a", "b", "c"))),
      )

      assertEquals(
        UrlForm.empty
          .+?("foo", 1)
          .+?(
            "bar",
            Option
              .empty[Boolean],
          )
          .++?("dummy", Chain("a", "b", "c")),
        UrlForm(Map("foo" -> Chain("1"), "dummy" -> Chain("a", "b", "c"))),
      )
    }

    test("UrlForm.single") {
      assertEquals(UrlForm.single("foo", "bar").get("foo"), Chain.one("bar"))
      assertEquals(UrlForm.single("foo", "bar").get("baz"), Chain.empty[String])
      assertEquals(UrlForm.single("", "bar"), UrlForm(Map("" -> Chain("bar"))))
    }

    test("UrlForm should construct consistently from kv-pairs or and Map[String, Chain[String]]") {
      Prop.forAll { (map: Map[String, NonEmptyList[String]]) =>
        // non-empty because the kv-constructor can't represent valueless fields
        val flattened =
          map.toList.flatMap(x => x._2.toList.fproductLeft(_ => x._1))
        UrlForm(flattened: _*) == UrlForm(
          CollectionCompat.mapValues(map)(nel => Chain.fromSeq(nel.toList))
        )
      }
    }

    test(
      "UrlForm should construct consistently from Chain of kv-pairs and Map[String, Chain[String]]"
    ) {
      Prop.forAll { (map: Map[String, NonEmptyList[String]]) =>
        // non-empty because the kv-constructor can't represent valueless fields
        val flattened =
          Chain.fromSeq(map.toList.flatMap(x => x._2.toList.fproductLeft(_ => x._1)))
        UrlForm.fromChain(flattened) == UrlForm(
          CollectionCompat.mapValues(map)(nel => Chain.fromSeq(nel.toList))
        )
      }
    }
  }

  checkAll("monoid", MonoidTests[UrlForm].monoid)

  {
    test("UrlForm monoid should use the obvious empty") {
      assertEquals(Monoid[UrlForm].empty, UrlForm.empty)
    }

    test("UrlForm monoid should combine two UrlForm instances") {
      val combined = UrlForm("foo" -> "1") |+| UrlForm("bar" -> "2", "foo" -> "3")
      assertEquals(combined, UrlForm("foo" -> "1", "bar" -> "2", "foo" -> "3"))
    }
  }
}
