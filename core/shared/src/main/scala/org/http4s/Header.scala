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

import cats.Foldable
import cats.Hash
import cats.Order
import cats.Semigroup
import cats.Show
import cats.data.Ior
import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.internal.CharPredicate
import org.http4s.util.Renderer
import org.http4s.util.StringWriter
import org.http4s.util.Writer
import org.typelevel.ci.CIString

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
  final case class Raw(name: CIString, value: String) {
    override def toString: String = Raw.toString(name, value)

    /** True if [[name]] is a valid field-name per RFC7230.  Where it
      * is not, the header may be dropped by the backend.
      */
    def isNameValid: Boolean =
      name.toString.nonEmpty && name.toString.forall(FieldNamePredicate)

    def sanitizedValue: String = {
      val w = new StringWriter
      w.sanitize(_ << value).result
    }
  }

  object Raw {
    @inline private[http4s] def toString(name: CIString, value: String): String =
      s"${name.toString}: $value"

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

      def render(writer: Writer, h: Raw): writer.type = {
        writer << h.name << ':' << ' '
        writer.sanitize(_ << h.value)
      }
    }
  }

  /** Classifies modelled headers into `Single` headers, which can only
    * appear once, and `Recurring` headers, which can appear multiple
    * times.
    */
  sealed trait Type
  case class Single() extends Type // scalafix:ok Http4sGeneralLinters; bincompat until 1.0
  case class Recurring() extends Type // scalafix:ok Http4sGeneralLinters; bincompat until 1.0

  def apply[A](implicit ev: Header[A, _]): ev.type = ev

  @deprecated("use Header.Raw.apply", "0.22.0")
  def apply(name: String, value: String): Header.Raw = Raw(CIString(name), value)

  def create[A, T <: Header.Type](
      name_ : CIString,
      value_ : A => String,
      parse_ : String => Either[ParseFailure, A],
  ): Header[A, T] = new Header[A, T] {
    def name: CIString = name_
    def value(a: A): String = value_(a)
    def parse(s: String): Either[ParseFailure, A] = parse_(s)
  }

  def createRendered[A, T <: Header.Type, B: Renderer](
      name_ : CIString,
      value_ : A => B,
      parse_ : String => Either[ParseFailure, A],
  ): Header[A, T] = new Header[A, T] {
    def name: CIString = name_
    def value(a: A): String = Renderer.renderString(value_(a))
    def parse(s: String): Either[ParseFailure, A] = parse_(s)
  }

  /** Target for implicit conversions to Header.Raw from modelled
    * headers and key-value pairs.
    *
    * A method taking variadic `ToRaw` arguments will allow taking
    * heterogeneous arguments, provided they are either:
    *
    * - A value of type `A`  which has a `Header[A]` in scope
    * - A (name, value) pair of `String`, which is treated as a `Recurring` header
    * - A `Header.Raw`
    * - A `Foldable` (`List`, `Option`, etc) of the above.
    *
    * @see [[org.http4s.Headers.apply]]
    */
  sealed trait ToRaw {
    def values: List[Header.Raw]
  }
  object ToRaw {
    trait Primitive

    implicit def identityToRaw(h: Header.ToRaw): Header.ToRaw with Primitive = new Header.ToRaw
      with Primitive {
      val values: List[Raw] = h.values
    }

    implicit def rawToRaw(h: Header.Raw): Header.ToRaw with Primitive =
      new Header.ToRaw with Primitive {
        val values = h :: Nil
      }

    implicit def keyValuesToRaw(kv: (String, String)): Header.ToRaw with Primitive =
      new Header.ToRaw with Primitive {
        val values = Header.Raw(CIString(kv._1), kv._2) :: Nil
      }

    implicit def headersToRaw(h: Headers): Header.ToRaw =
      new Header.ToRaw {
        val values: List[Raw] = h.headers
      }

    implicit def modelledHeadersToRaw[H](
        h: H
    )(implicit H: Header[H, _]): Header.ToRaw with Primitive =
      new Header.ToRaw with Primitive {
        val values = Header.Raw(H.name, H.value(h)) :: Nil
      }

    implicit def foldablesToRaw[F[_]: Foldable, H](
        h: F[H]
    )(implicit convert: H => ToRaw with Primitive): Header.ToRaw = new Header.ToRaw {
      val values = h
        .foldLeft(List.newBuilder[Header.Raw]) { (buf, v) =>
          buf ++= convert(v).values
        }
        .result()
    }

    // Required for 2.12 to convert variadic args.
    implicit def scalaCollectionSeqToRaw[H](
        h: collection.Seq[H]
    )(implicit convert: H => ToRaw with Primitive): Header.ToRaw = new Header.ToRaw {
      val values = {
        val buf = List.newBuilder[Header.Raw]
        h.foreach(buf ++= convert(_).values)
        buf.result()
      }
    }
  }

  /** Abstracts over Single and Recurring Headers
    */
  sealed trait Select[A] {
    type F[_]

    /** Transform this header into a [[Header.Raw]]
      */
    def toRaw1(a: A): Header.Raw

    /** Transform this (potentially repeating) header into a [[Header.Raw]] */
    def toRaw(a: F[A]): NonEmptyList[Header.Raw]

    /** Selects this header from a list of [[Header.Raw]]
      */
    def from(headers: List[Header.Raw]): Option[Ior[NonEmptyList[ParseFailure], F[A]]]
  }
  trait LowPrio {
    implicit def recurringHeadersNoMerge[A](implicit
        h: Header[A, Header.Recurring]
    ): Select[A] { type F[B] = NonEmptyList[B] } =
      new Select[A] {
        type F[B] = NonEmptyList[B]

        def toRaw1(a: A): Header.Raw =
          Header.Raw(h.name, h.value(a))

        def toRaw(as: F[A]): NonEmptyList[Header.Raw] =
          as.map(a => Header.Raw(h.name, h.value(a)))

        def from(headers: List[Raw]): Option[Ior[NonEmptyList[ParseFailure], NonEmptyList[A]]] =
          headers.foldLeft(Option.empty[Ior[NonEmptyList[ParseFailure], NonEmptyList[A]]]) {
            (a, raw) =>
              Select.fromRaw(raw) match {
                case Some(aa) => a |+| aa.bimap(NonEmptyList.one, NonEmptyList.one).some
                case None => a
              }
          }
      }
  }
  object Select extends LowPrio {
    type Aux[A, G[_]] = Select[A] { type F[B] = G[B] }

    def fromRaw[A](h: Header.Raw)(implicit ev: Header[A, _]): Option[Ior[ParseFailure, A]] =
      if (h.name == Header[A].name) Some(Header[A].parse(h.value).toIor) else None

    implicit def singleHeaders[A](implicit
        h: Header[A, Header.Single]
    ): Select[A] { type F[B] = B } =
      new Select[A] {
        type F[B] = B

        def toRaw1(a: A): Header.Raw =
          Header.Raw(h.name, h.value(a))

        def toRaw(a: A): NonEmptyList[Header.Raw] =
          NonEmptyList.one(toRaw1(a))

        def from(headers: List[Raw]): Option[Ior[NonEmptyList[ParseFailure], F[A]]] =
          headers.collectFirst(Function.unlift(fromRaw(_).map(_.leftMap(NonEmptyList.one))))
      }

    implicit def recurringHeadersWithMerge[A: Semigroup](implicit
        h: Header[A, Header.Recurring]
    ): Select[A] { type F[B] = B } =
      new Select[A] {
        type F[B] = B

        def toRaw1(a: A): Header.Raw =
          Header.Raw(h.name, h.value(a))

        def toRaw(a: A): NonEmptyList[Header.Raw] =
          NonEmptyList.one(toRaw1(a))

        def from(headers: List[Raw]): Option[Ior[NonEmptyList[ParseFailure], F[A]]] =
          headers.foldLeft(Option.empty[Ior[NonEmptyList[ParseFailure], F[A]]]) { (a, raw) =>
            fromRaw(raw) match {
              case Some(aa) => a |+| aa.leftMap(NonEmptyList.one).some
              case None => a
            }
          }
      }
  }

  private val FieldNamePredicate =
    CharPredicate("!#$%&'*+-.^_`|~`") ++ CharPredicate.AlphaNum
}
