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
package v2

import cats.{Foldable, Hash, Monoid, Order, Semigroup, Show}
import cats.data.NonEmptyList
import cats.syntax.all._
import org.typelevel.ci.CIString
import scala.collection.mutable
import org.http4s.util.{Renderer, Writer}

/** Typeclass representing an HTTP header, which all the http4s
  * default headers satisfy.
  * You can add modelled headers by providing an implicit instance of
  * `Header[YourModelledHeader]`
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
  def parse(headerValue: String): Either[ParseFailure, A]
}
object Header {
  final case class Raw(val name: CIString, val value: String) {
    override def toString: String = s"${name}: ${value}"
  }

  object Raw {
    implicit lazy val catsInstancesForHttp4sHeaderRaw
        : Order[Raw] with Hash[Raw] with Show[Raw] with Renderer[Raw] = new Order[Raw]
      with Hash[Raw]
      with Show[Raw]
      with Renderer[Raw] {
      def show(h: Raw): String = s"${h.name.show}: ${h.value}"
      def hash(h: Raw): Int = h.hashCode

      def compare(x: Raw, y: Raw): Int =
        x.name.compare(y.name) match {
          case 0 => x.value.compare(y.value)
          case c => c
        }

      def render(writer: Writer, h: Raw): writer.type =
        writer << h.name << ':' << ' ' << h.value
    }
  }

  /** Classifies modelled headers into `Single` headers, which can only
    * appear once, and `Recurring` headers, which can appear multiple
    * times.
    */
  sealed trait Type
  case class Single() extends Type
  case class Recurring() extends Type

  def apply[A](implicit ev: Header[A, _]): ev.type = ev

  def create[A, T <: Header.Type](
      name_ : CIString,
      value_ : A => String,
      parse_ : String => Either[ParseFailure, A]): Header[A, T] = new Header[A, T] {
    def name = name_
    def value(a: A) = value_(a)
    def parse(s: String) = parse_(s)
  }

  def createRendered[A, T <: Header.Type, B: Renderer](
      name_ : CIString,
      value_ : A => B,
      parse_ : String => Either[ParseFailure, A]): Header[A, T] = new Header[A, T] {
    def name = name_
    def value(a: A) = Renderer.renderString(value_(a))
    def parse(s: String) = parse_(s)
  }

  /** Target for implicit conversions to Header.Raw from modelled
    * headers and key-value pairs.
    *
    * A method taking variadic `ToRaw` arguments will allow taking
    * heteregenous arguments, provided they are either:
    *
    * - A value of type `A`  which has a `Header[A]` in scope
    * - A (name, value) pair of `String`, which is treated as a `Recurring` header
    * - A `Header.Raw`
    * - A `Foldable` (`List`, `Option`, etc) of the above.
    *
    * @see [[org.http4s.Headers$.apply]]
    */
  sealed trait ToRaw {
    def values: List[Header.Raw]
  }
  object ToRaw {
    trait Primitive

    implicit def identityToRaw(h: Header.ToRaw): Header.ToRaw with Primitive = new Header.ToRaw
    with Primitive {
      val values = h.values
    }

    implicit def rawToRaw(h: Header.Raw): Header.ToRaw with Primitive =
      new Header.ToRaw with Primitive {
        val values = List(h)
      }

    implicit def keyValuesToRaw(kv: (String, String)): Header.ToRaw with Primitive =
      new Header.ToRaw with Primitive {
        val values = List(Header.Raw(CIString(kv._1), kv._2))
      }

    implicit def modelledHeadersToRaw[H](h: H)(implicit
        H: Header[H, _]): Header.ToRaw with Primitive =
      new Header.ToRaw with Primitive {
        val values = List(Header.Raw(H.name, H.value(h)))
      }

    implicit def foldablesToRaw[F[_]: Foldable, H](h: F[H])(implicit
        convert: H => ToRaw with Primitive): Header.ToRaw = new Header.ToRaw {
      val values = h.toList.foldMap(v => convert(v).values)
    }

    // Required for 2.12 to convert variadic args.
    implicit def scalaCollectionSeqToRaw[H](h: collection.Seq[H])(implicit
        convert: H => ToRaw with Primitive): Header.ToRaw = new Header.ToRaw {
      val values = h.toList.foldMap(v => convert(v).values)
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
      (h.name == Header[A].name).guard[Option] >> Header[A].parse(h.value).toOption

    implicit def singleHeaders[A](implicit
        h: Header[A, Header.Single]): Select[A] { type F[B] = B } =
      new Select[A] {
        type F[B] = B

        def toRaw(a: A): Header.Raw =
          Header.Raw(h.name, h.value(a))

        def from(headers: List[Header.Raw]): Option[A] =
          headers.collectFirst(Function.unlift(fromRaw(_)))
      }

    implicit def recurringHeadersWithMerge[A: Semigroup](implicit
        h: Header[A, Header.Recurring]): Select[A] { type F[B] = B } =
      new Select[A] {
        type F[B] = B

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

  def transform(f: List[Header.Raw] => List[Header.Raw]): Headers =
    Headers(f(headers))

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
    this ++ Headers(in.values)

  def ++(those: Headers): Headers =
    if (those.headers.isEmpty) this
    else if (this.headers.isEmpty) those
    else {
      val thoseNames = mutable.Set.empty[CIString]
      those.headers.foreach(h => thoseNames.add(h.name))
      Headers(headers.filterNot(h => thoseNames.contains(h.name)) ++ those.headers)
    }

  def add[H: Header[*, Header.Recurring]](h: H): Headers =
    Headers(this.headers ++ Header.ToRaw.modelledHeadersToRaw(h).values)

  /** Removes the `Content-Length`, `Content-Range`, `Trailer`, and
    * `Transfer-Encoding` headers.
    *
    *  https://tools.ietf.org/html/rfc7231#section-3.3
    */
  def removePayloadHeaders: Headers =
    transform(_.filterNot(h => Headers.PayloadHeaderKeys(h.name)))

  def redactSensitive(
      redactWhen: CIString => Boolean = Headers.SensitiveHeaders.contains): Headers =
    transform {
      _.map {
        case h if redactWhen(h.name) => Header.Raw(h.name, "<REDACTED>")
        case h => h
      }
    }

  def foreach(f: Header.Raw => Unit): Unit = headers.foreach(f)

  override def toString: String =
    this.show
}
object Headers {
  val empty = Headers(List.empty[Header.Raw])

  /** Creates a new Headers collection.
    * The [[Header.ToRaw]] machinery allows the creation of Headers with
    * variadic and heteregenous arguments, provided they are either:
    * - A value of type `A`  which has a `Header[A]` in scope
    * - A (name, value) pair of `String`
    * - A `Header.Raw`
    * - A `Foldable` (`List`, `Option`, etc) of the above.
    */
  def apply(headers: Header.ToRaw*): Headers =
    new Headers(headers.values)

  implicit val headersShow: Show[Headers] =
    _.headers.iterator.map(_.show).mkString("Headers(", ", ", ")")

  implicit lazy val HeadersOrder: Order[Headers] =
    Order.by(_.headers)

  implicit val headersMonoid: Monoid[Headers] = new Monoid[Headers] {
    def empty: Headers = Headers.empty
    def combine(xa: Headers, xb: Headers): Headers =
      xa ++ xb
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
