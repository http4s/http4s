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

package com.examples.http4s

import cats.Semigroup
import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s._
import org.typelevel.ci._

// TODO migrate to a proper mdoc. This is to keep it compiling.

object HeaderExamples {
  // /// test for construction
  final case class Foo(v: String)
  object Foo {
    implicit def headerFoo: Header[Foo, Header.Single] = new Header[Foo, Header.Single] {
      def name = ci"foo"
      def value(f: Foo) = f.v
      def parse(s: String) = Foo(s).asRight
    }

  }
  def baz: Header.Raw = Header.Raw(ci"baz", "bbb")

  val myHeaders: Headers = Headers(
    Foo("hello"),
    "my" -> "header",
    baz,
  )
  // //// test for selection
  final case class Bar(v: NonEmptyList[String])
  object Bar {
    implicit val headerBar: Header[Bar, Header.Recurring] with Semigroup[Bar] =
      new Header[Bar, Header.Recurring] with Semigroup[Bar] {
        def name = ci"Bar"
        def value(b: Bar) = b.v.toList.mkString(",")
        def parse(s: String) = Bar(NonEmptyList.one(s)).asRight
        def combine(a: Bar, b: Bar) = Bar(a.v |+| b.v)
      }
  }

  final case class SetCookie(name: String, value: String)
  object SetCookie {
    implicit val headerCookie: Header[SetCookie, Header.Recurring] =
      new Header[SetCookie, Header.Recurring] {
        def name = ci"Set-Cookie"
        def value(c: SetCookie) = s"${c.name}:${c.value}"
        def parse(s: String) =
          s.split(':').toList match {
            case List(name, value) => SetCookie(name, value).asRight
            case _ => Left(ParseFailure("Malformed cookie", ""))
          }
      }
  }

  val hs: Headers = Headers(
    Bar(NonEmptyList.one("one")),
    Foo("two"),
    SetCookie("cookie1", "a cookie"),
    Bar(NonEmptyList.one("three")),
    SetCookie("cookie2", "another cookie"),
  )

  val a: Option[Foo] = hs.get[Foo]
  val b: Option[Bar] = hs.get[Bar]
  val c: Option[NonEmptyList[SetCookie]] = hs.get[SetCookie]

  // scala> Examples.a
  // val res0: Option[Foo] = Some(Foo(two))

  // scala> Examples.b
  // val res1: Option[Bar] = Some(Bar(NonEmptyList(one, three)))

  // scala> Examples.c
  // val res2: Option[NonEmptyList[SetCookie]] = Some(NonEmptyList(SetCookie(cookie1,a cookie), SetCookie(cookie2,another cookie)))

  val hs2: Headers = Headers(
    Bar(NonEmptyList.one("one")),
    Foo("two"),
    SetCookie("cookie1", "a cookie"),
    Bar(NonEmptyList.one("three")),
    SetCookie("cookie2", "another cookie"),
    "a" -> "b",
    Option("a" -> "c"),
    List("a" -> "c"),
    List(SetCookie("cookie3", "cookie three")),
    // ,
    // Option(List("a" -> "c")) // correctly fails to compile
  )

}
