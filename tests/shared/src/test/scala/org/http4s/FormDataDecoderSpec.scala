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

import cats.data.Chain
import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.syntax.all._

import FormDataDecoder._

class FormDataDecoderSpec extends Http4sSuite {

  {
    val mapper = field[Boolean]("a")

    test("Field decoder should decode a field successfully") {
      assertEquals(mapper(Map("a" -> Chain("true"))), Valid(true))
    }

    test("Field decoder should report error when field is missing") {
      assertEquals(mapper(Map("b" -> Chain("true"))), ParseFailure("a is missing", "").invalidNel)
    }

    test("Field decoder should report error when field is wrong form") {
      assertEquals(
        mapper(Map("a" -> Chain("1"))),
        ParseFailure(
          "Query decoding Boolean failed",
          "For input string: \"1\"",
        ).invalidNel,
      )
    }
  }

  {
    val mapper = fieldOptional[Boolean]("a")
    test("Field Optional Decoder should return Some if the field is missing") {
      assertEquals(mapper(Map("a" -> Chain("true"))), Valid(Some(true)))
    }

    test("Field Optional Decoder should return None if the field is missing") {
      assertEquals(mapper(Map("b" -> Chain("true"))), Valid(None))
    }
  }

  {
    val mapper = fieldEither[Boolean]("a").default(false)
    test("Field default Decoder should return defaultValue if the field is missing") {
      assertEquals(mapper(Map()), Valid(false))
    }

  }

  {
    final case class Foo(a: String, b: Boolean)

    implicit val fooMapper: FormDataDecoder[Foo] =
      (field[String]("a"), field[Boolean]("b")).mapN(Foo.apply)

    test("mapN to decode case class should map successfully for valid data") {
      assertEquals(
        fooMapper(Map("a" -> Chain("bar"), "b" -> Chain("false"))),
        Valid(
          Foo("bar", false)
        ),
      )
    }

    test("mapN to decode case class should accumulate errors for invalid data") {
      assertEquals(
        fooMapper(Map("b" -> Chain("1"))),
        Invalid(
          NonEmptyList.of(
            ParseFailure("a is missing", ""),
            ParseFailure("Query decoding Boolean failed", "For input string: \"1\""),
          )
        ),
      )
    }

    final case class FooStrings(a: List[String])

    implicit val fooStringMapper: FormDataDecoder[FooStrings] =
      listOf[String]("a").map(FooStrings.apply)

    test("mapN to decode case class should decode list successfully for valid data") {
      assertEquals(
        fooStringMapper(Map("a[]" -> Chain("bar1", "bar2"))),
        Valid(
          FooStrings(List("bar1", "bar2"))
        ),
      )
    }

    test("mapN to decode case class should decode empty list when data is missing") {
      assertEquals(
        fooStringMapper(Map()),
        Valid(
          FooStrings(Nil)
        ),
      )
    }

    final case class FooNested(f: Foo, c: String)

    val fooNestedMapper: FormDataDecoder[FooNested] = (
      nested[Foo]("f"),
      field[String]("c"),
    ).mapN(FooNested.apply)

    test("mapN to decode case class should map nested case class") {
      assertEquals(
        fooNestedMapper(
          Map(
            "c" -> Chain("ccc"),
            "f.a" -> Chain("bar"),
            "f.b" -> Chain("true"),
          )
        ),
        Valid(FooNested(Foo("bar", true), "ccc")),
      )
    }

    final case class FooNestedOptional(f: Option[Foo], c: Option[String])

    val fooNestedOptionalMapper = (
      nestedOptional[Foo]("f"),
      fieldOptional[String]("c"),
    ).mapN(FooNestedOptional.apply)

    test("mapN to decode case class should set values to None if missing") {
      assertEquals(
        fooNestedOptionalMapper(
          Map(
          )
        ),
        Valid(FooNestedOptional(None, None)),
      )
    }

    test("mapN to decode case class should set values to Value if missing") {
      assertEquals(
        fooNestedOptionalMapper(
          Map(
            "c" -> Chain("ccc"),
            "f.a" -> Chain("bar"),
            "f.b" -> Chain("true"),
          )
        ),
        Valid(FooNestedOptional(Option(Foo("bar", true)), Option("ccc"))),
      )
    }

    final case class FooList(fs: List[Foo], d: Boolean)

    val fooListMapper: FormDataDecoder[FooList] = (
      list[Foo]("fs"),
      field[Boolean]("d"),
    ).mapN(FooList.apply)

    test("mapN to decode case class should map nested list of nested data") {
      assertEquals(
        fooListMapper(
          Map(
            "d" -> Chain("false"),
            "fs[].a" -> Chain("f1", "f2"),
            "fs[].b" -> Chain("false", "true"),
          )
        ),
        Valid(FooList(List(Foo("f1", false), Foo("f2", true)), false)),
      )
    }

    test(
      "mapN to decode case class should map nested list of nested data to empty list if missing"
    ) {
      assertEquals(
        fooListMapper(
          Map(
            "d" -> Chain("false")
          )
        ),
        Valid(FooList(List.empty[Foo], false)),
      )
    }

  }

}
