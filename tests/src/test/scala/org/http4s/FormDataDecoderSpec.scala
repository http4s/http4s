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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Chain, NonEmptyList}
import cats.syntax.all._
import FormDataDecoder._

class FormDataDecoderSpec extends Http4sSpec {

  "Field decoder" should {
    val mapper = field[Boolean]("a")

    "decode a field successfully" in {
      mapper(Map("a" -> Chain("true"))) must_=== Valid(true)
    }

    "report error when field is missing" in {
      mapper(Map("b" -> Chain("true"))) must_=== ParseFailure("a is missing", "").invalidNel
    }

    "report error when field is wrong form" in {
      mapper(Map("a" -> Chain("1"))) must_=== ParseFailure(
        "Query decoding Boolean failed",
        "For input string: \"1\""
      ).invalidNel
    }
  }

  "Field Optional Decoder" should {
    val mapper = fieldOptional[Boolean]("a")
    "return Some if the field is missing" in {
      mapper(Map("a" -> Chain("true"))) must_== Valid(Some(true))
    }

    "return None if the field is missing" in {
      mapper(Map("b" -> Chain("true"))) must_== Valid(None)
    }
  }

  "Field default Decoder" should {
    val mapper = fieldEither[Boolean]("a").default(false)
    "return defaultValue if the field is missing" in {
      mapper(Map()) must_== Valid(false)
    }

  }

  "mapN to decode case class" should {
    case class Foo(a: String, b: Boolean)

    implicit val fooMapper =
      (field[String]("a"), field[Boolean]("b")).mapN(Foo.apply)

    "map successfully for valid data" in {
      fooMapper(Map("a" -> Chain("bar"), "b" -> Chain("false"))) must_=== Valid(
        Foo("bar", false)
      )
    }

    "accumulate errors for invalid data" in {
      fooMapper(Map("b" -> Chain("1"))) must_=== Invalid(
        NonEmptyList.of(
          ParseFailure("a is missing", ""),
          ParseFailure("Query decoding Boolean failed", "For input string: \"1\"")
        )
      )
    }

    case class FooStrings(a: List[String])

    implicit val fooStringMapper =
      listOf[String]("a").map(FooStrings.apply)

    "decode list successfully for valid data" in {
      fooStringMapper(Map("a[]" -> Chain("bar1", "bar2"))) must_=== Valid(
        FooStrings(List("bar1", "bar2"))
      )
    }

    "decode empty list when data is missing" in {
      fooStringMapper(Map()) must_=== Valid(
        FooStrings(Nil)
      )
    }

    case class FooNested(f: Foo, c: String)

    val fooNestedMapper = (
      nested[Foo]("f"),
      field[String]("c")
    ).mapN(FooNested.apply)

    "map nested case class" in {
      fooNestedMapper(
        Map(
          "c" -> Chain("ccc"),
          "f.a" -> Chain("bar"),
          "f.b" -> Chain("true")
        )
      ) must_=== Valid(FooNested(Foo("bar", true), "ccc"))
    }

    case class FooNestedOptional(f: Option[Foo], c: Option[String])

    val fooNestedOptionalMapper = (
      nestedOptional[Foo]("f"),
      fieldOptional[String]("c")
    ).mapN(FooNestedOptional.apply)

    "set values to None if missing" in {
      fooNestedOptionalMapper(
        Map(
        )
      ) must_=== Valid(FooNestedOptional(None, None))
    }

    "set values to Value if missing" in {
      fooNestedOptionalMapper(
        Map(
          "c" -> Chain("ccc"),
          "f.a" -> Chain("bar"),
          "f.b" -> Chain("true")
        )
      ) must_=== Valid(FooNestedOptional(Option(Foo("bar", true)), Option("ccc")))
    }

    case class FooList(fs: List[Foo], d: Boolean)

    val fooListMapper = (
      list[Foo]("fs"),
      field[Boolean]("d")
    ).mapN(FooList.apply)

    "map nested list of nested data" in {
      fooListMapper(
        Map(
          "d" -> Chain("false"),
          "fs[].a" -> Chain("f1", "f2"),
          "fs[].b" -> Chain("false", "true")
        )
      ) must_=== Valid(FooList(List(Foo("f1", false), Foo("f2", true)), false))
    }

    "map nested list of nested data to empty list if missing" in {
      fooListMapper(
        Map(
          "d" -> Chain("false")
        )
      ) must_=== Valid(FooList(List.empty[Foo], false))
    }

  }

}
