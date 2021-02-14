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

package org.http4s.header.v2

import cats.{Id, Monoid, Order, Semigroup, Show}
import cats.data.NonEmptyList
import cats.syntax.all._
import org.typelevel.ci.CIString
import scala.collection.mutable.{ListBuffer, Set => MutSet}

/** Typeclass representing an HTTP header, which all the http4s
  * default headers satisfy.
  * You can add custom headers by providing an implicit instance of
  * `Header[YourCustomHeader]`
  */
trait Header[A, T <: Header.Type] {

  /** Name of the header. Not case sensitive.
    */
  def name: CIString

  /** Value of the header, which is represented as a String.
    * Will be a comma separated String for headers with multiple values.
    */
  def value(a: A): String

  /** Parses the header from its String representation.
    * Could be a comma separated String in case of a Header with
    * multiple values.
    */
  def parse(headerValue: String): Option[A]
}
object Header {
  case class Raw(name: CIString, value: String, recurring: Boolean = true)
  object Raw {
    implicit val showForRawHeaders: Show[Raw] =
      h => s"${h.name.show}: ${h.value}"

    implicit lazy val orderForRawHeaders: Order[Raw] =
      Order.whenEqual(
        Order.by(_.name),
        Order.by(_.value)
      )
  }

  /** Classifies custom headers into `Single` headers, which can only
    * appear once, and `Recurring` headers, which can appear multiple
    * times.
    */
  sealed trait Type
  case class Single() extends Type
  case class Recurring() extends Type

  def apply[A](implicit ev: Header[A, _]): ev.type = ev

  /** Target for implicit conversions to Header.Raw from custom
    * headers and key-value pairs.
    *
    * A method taking variadic `ToRaw` arguments will allow taking
    * heteregenous arguments, provided they are either:
    *
    * - A value of type `A`  which has a `Header[A]` in scope
    * - A (name, value) pair of `String`, which is treated as a `Recurring` header
    * - A `Header.Raw`
    *
    * @see [[org.http4s.Headers$.apply]]
    */
  sealed trait ToRaw {
    def value: Header.Raw
  }
  object ToRaw {
    implicit def rawToRaw(h: Header.Raw): Header.ToRaw = new Header.ToRaw {
      val value = h
    }

    implicit def keyValuesToRaw(kv: (String, String)): Header.ToRaw = new Header.ToRaw {
      val value = Header.Raw(CIString(kv._1), kv._2)
    }

    implicit def customHeadersToRaw[H](h: H)(implicit H: Header[H, _]): Header.ToRaw =
      new Header.ToRaw {
        val value = Header.Raw(H.name, H.value(h))
      }
  }

  /** Abstracts over Single and Recurring Headers
    */
  sealed trait Select[A] {
    type F[_]

    /** Transform this header into a [[Header.Raw]]
      */
    def toRaw(a: A): Header.Raw

    /** Selects this header from a list of [[Header.Raw]]
      */
    def from(headers: List[Header.Raw]): Option[F[A]]
  }
  trait LowPrio {
    implicit def recurringHeadersNoMerge[A](implicit
        h: Header[A, Header.Recurring]): Select[A] { type F[B] = NonEmptyList[B] } =
      new Select[A] {
        type F[B] = NonEmptyList[B]

        def toRaw(a: A): Header.Raw =
          Header.Raw(h.name, h.value(a))

        def from(headers: List[Header.Raw]): Option[NonEmptyList[A]] =
          headers.collect(Function.unlift(Select.fromRaw(_))).toNel
      }
  }
  object Select extends LowPrio {
    def fromRaw[A](h: Header.Raw)(implicit ev: Header[A, _]): Option[A] =
      (h.name == Header[A].name).guard[Option] >> Header[A].parse(h.value)

    implicit def singleHeaders[A](implicit
        h: Header[A, Header.Single]): Select[A] { type F[B] = Id[B] } =
      new Select[A] {
        type F[B] = Id[B]

        def toRaw(a: A): Header.Raw =
          Header.Raw(h.name, h.value(a), recurring = false)

        def from(headers: List[Header.Raw]): Option[A] =
          headers.collectFirst(Function.unlift(fromRaw(_)))
      }

    implicit def recurringHeadersWithMerge[A: Semigroup](implicit
        h: Header[A, Header.Recurring]): Select[A] { type F[B] = Id[B] } =
      new Select[A] {
        type F[B] = Id[B]

        def toRaw(a: A): Header.Raw =
          Header.Raw(h.name, h.value(a))

        def from(headers: List[Header.Raw]): Option[A] =
          headers.foldLeft(Option.empty[A]) { (a, raw) =>
            fromRaw(raw) match {
              case Some(aa) => a |+| aa.some
              case None => a
            }
          }
      }
  }
}

/** A collection of HTTP Headers */
final class Headers(val headers: List[Header.Raw]) extends AnyVal {

  /** TODO revise scaladoc
    * Attempt to get a [[org.http4s.Header]] of type key.HeaderT from this collection
    *
    * @param key [[HeaderKey.Extractable]] that can identify the required header
    * @return a scala.Option possibly containing the resulting header of type key.HeaderT
    * @see [[Header]] object and get([[org.typelevel.ci.CIString]])
    */
  def get[A](implicit ev: Header.Select[A]): Option[ev.F[A]] =
    ev.from(headers)

  /** TODO revise scaladoc
    * Attempt to get a [[org.http4s.Header]] from this collection of headers
    *
    * @param key name of the header to find
    * @return a scala.Option possibly containing the resulting [[org.http4s.Header]]
    */
  def get(key: CIString): Option[NonEmptyList[Header.Raw]] = headers.filter(_.name == key).toNel

  /** Make a new collection adding the specified headers, replacing existing `Single` headers.
    *
    * @param in multiple heteregenous headers [[Header]] to append to the new collection, see [[Header.ToRaw]]
    * @return a new [[Headers]] containing the sum of the initial and input headers
    */
  def put(in: Header.ToRaw*): Headers =
    if (in.isEmpty) this
    else if (this.headers.isEmpty) Headers(in: _*)
    else Headers.of(headers ++ in.toList.map(_.value))

  /** Removes the `Content-Length`, `Content-Range`, `Trailer`, and
    * `Transfer-Encoding` headers.
    *
    *  https://tools.ietf.org/html/rfc7231#section-3.3
    */
  def removePayloadHeaders: Headers =
    Headers.of(headers.filterNot(h => Headers.PayloadHeaderKeys(h.name)))

  def redactSensitive(
      redactWhen: CIString => Boolean = Headers.SensitiveHeaders.contains): Headers =
    Headers.of {
      headers.map {
        case h if redactWhen(h.name) => Header.Raw(h.name, "<REDACTED>")
        case h => h
      }
    }

  override def toString: String =
    this.show
}
object Headers {
  val empty = of(List.empty)

  /** Creates a new Headers collection.
    * The [[Header.ToRaw]] machinery allows the creation of Headers with
    * variadic and heteregenous arguments, provided they are either:
    * - A value of type `A`  which has a `Header[A]` in scope
    * - A (name, value) pair of `String`
    * - A `Header.Raw`
    */
  def apply(headers: Header.ToRaw*): Headers =
    of(headers.toList.map(_.value))

  /** Creates a new Headers collection from the headers
    * Deduplicates non-recurring headers.
    */
  def of(headers: List[Header.Raw]): Headers =
    if (headers.isEmpty) new Headers(List.empty)
    else {
      val acc = MutSet.empty[CIString]
      val res = ListBuffer.empty[Header.Raw]

      headers.foreach { h =>
        if (h.recurring || h.name == "Set-Cookie" || acc.add(h.name))
          res += h
      }

      new Headers(res.toList)
    }

  implicit val headersShow: Show[Headers] =
    _.headers.iterator.map(_.show).mkString("Headers(", ", ", ")")

  implicit lazy val HeadersOrder: Order[Headers] =
    Order.by(_.headers)

  implicit val headersMonoid: Monoid[Headers] = new Monoid[Headers] {
    def empty: Headers = Headers.empty
    def combine(xa: Headers, xb: Headers): Headers =
      Headers.of(xa.headers ++ xb.headers)
  }

  private val PayloadHeaderKeys = Set(
    CIString("Content-Length"),
    CIString("Content-Range"),
    CIString("Trailer"),
    CIString("Transfer-Encoding")
  )

  val SensitiveHeaders = Set(
    CIString("Authorization"),
    CIString("Cookie"),
    CIString("Set-Cookie")
  )
}

object Examples {
  ///// test for construction
  case class Foo(v: String)
  object Foo {
    implicit def headerFoo: Header[Foo, Header.Single] = new Header[Foo, Header.Single] {
      def name = CIString("foo")
      def value(f: Foo) = f.v
      def parse(s: String) = Foo(s).some
    }

  }
  def baz = Header.Raw(CIString("baz"), "bbb")

  val myHeaders = Headers(
    Foo("hello"),
    "my" -> "header",
    baz
  )
  ////// test for selection
  case class Bar(v: NonEmptyList[String])
  object Bar {
    implicit val headerBar: Header[Bar, Header.Recurring] with Semigroup[Bar] =
      new Header[Bar, Header.Recurring] with Semigroup[Bar] {
        def name = CIString("Bar")
        def value(b: Bar) = b.v.toList.mkString(",")
        def parse(s: String) = Bar(NonEmptyList.one(s)).some
        def combine(a: Bar, b: Bar) = Bar(a.v |+| b.v)
      }
  }

  case class SetCookie(name: String, value: String)
  object SetCookie {
    implicit val headerCookie: Header[SetCookie, Header.Recurring] =
      new Header[SetCookie, Header.Recurring] {
        def name = CIString("Set-Cookie")
        def value(c: SetCookie) = s"${c.name}:${c.value}"
        def parse(s: String) =
          s.split(':').toList match {
            case List(name, value) => SetCookie(name, value).some
            case _ => None
          }
      }
  }

  val hs = Headers(
    Bar(NonEmptyList.one("one")),
    Foo("two"),
    SetCookie("cookie1", "a cookie"),
    Bar(NonEmptyList.one("three")),
    SetCookie("cookie2", "another cookie")
  )

  val a = hs.get[Foo]
  val b = hs.get[Bar]
  val c = hs.get[SetCookie]

  // scala> Examples.a
  // val res0: Option[Foo] = Some(Foo(two))

  // scala> Examples.b
  // val res1: Option[Bar] = Some(Bar(NonEmptyList(one, three)))

  // scala> Examples.c
  // val res2: Option[NonEmptyList[SetCookie]] = Some(NonEmptyList(SetCookie(cookie1,a cookie), SetCookie(cookie2,another cookie)))

}
