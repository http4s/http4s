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
import cats.Order
import cats.Show
import cats.data.Ior
import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.implicits.http4sSelectSyntaxOne
import org.typelevel.ci._

import scala.collection.mutable

import headers._

/** A collection of HTTP Headers */
final class Headers(val headers: List[Header.Raw]) extends AnyVal {

  def transform(f: List[Header.Raw] => List[Header.Raw]): Headers =
    Headers(f(headers))

  /** Attempt to get a (potentially repeating) header from this collection of headers.
    *
    * @return a scala.Option possibly containing the resulting (potentially repeating) header.
    */
  def get[A](implicit ev: Header.Select[A]): Option[ev.F[A]] =
    ev.from(headers).flatMap(_.toOption)

  /** Attempt to get a (potentially repeating) header and/or any parse errors from this collection of headers.
    *
    * @return a scala.Option possibly containing the resulting (potentially repeating) header
    *         and/or any parse errors.
    */
  def getWithWarnings[A](implicit
      ev: Header.Select[A]
  ): Option[Ior[NonEmptyList[ParseFailure], ev.F[A]]] =
    ev.from(headers)

  /** Returns true if there is at least one header by the specified name. */
  def contains[A](implicit ev: Header[A, _]): Boolean =
    headers.exists(_.name == ev.name)

  /** Attempt to get headers by key from this collection of headers.
    *
    * @param key name of the headers to find.
    * @return a scala.Option possibly containing the resulting collection
    *         [[cats.data.NonEmptyList]] of [[org.http4s.Header.Raw]].
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
    *  https://datatracker.ietf.org/doc/html/rfc7231#section-3.3
    */
  def removePayloadHeaders: Headers =
    transform(_.filterNot(h => Headers.PayloadHeaderKeys(h.name)))

  /** Puts a `Content-Length` header, replacing any existing.  Removes
    * any existing `chunked` value from the `Transfer-Encoding`
    * header.  It is critical that the supplied content length
    * accurately describe the length of the body stream.
    *
    * {{{
    * scala> import org.http4s.headers._
    * scala> val chunked = Headers(
    *      |   `Transfer-Encoding`(TransferCoding.chunked),
    *      |   `Content-Type`(MediaType.text.plain))
    * scala> chunked.withContentLength(`Content-Length`.unsafeFromLong(1024))
    * res0: Headers = Headers(Content-Length: 1024, Content-Type: text/plain)
    *
    * scala> val chunkedGzipped = Headers(
    *      |   `Transfer-Encoding`(TransferCoding.chunked, TransferCoding.gzip),
    *      |   `Content-Type`(MediaType.text.plain))
    * scala> chunkedGzipped.withContentLength(`Content-Length`.unsafeFromLong(1024))
    * res1: Headers = Headers(Content-Length: 1024, Transfer-Encoding: gzip, Content-Type: text/plain)
    *
    * scala> val const = Headers(
    *      |   `Content-Length`(2048),
    *      |   `Content-Type`(MediaType.text.plain))
    * scala> const.withContentLength(`Content-Length`.unsafeFromLong(1024))
    * res1: Headers = Headers(Content-Length: 1024, Content-Type: text/plain)
    * }}}
    */
  def withContentLength(contentLength: `Content-Length`): Headers =
    transform { hs =>
      val b = List.newBuilder[Header.Raw]
      b += contentLength.toRaw1
      hs.foreach { h =>
        h.name match {
          case `Transfer-Encoding`.name =>
            `Transfer-Encoding`
              .parse(h.value)
              .redeem[Unit](
                _ => b += h,
                _.filter(_ != TransferCoding.chunked)
                  .foreach(b += _.toRaw1),
              )
          case `Content-Length`.name =>
            ()
          case _ =>
            b += h
        }
      }
      b.result()
    }

  def redactSensitive(
      redactWhen: CIString => Boolean = Headers.SensitiveHeaders.contains
  ): Headers =
    transform {
      _.map {
        case h if redactWhen(h.name) => Header.Raw(h.name, "<REDACTED>")
        case h => h
      }
    }

  def foreach(f: Header.Raw => Unit): Unit = headers.foreach(f)

  /** Creates a string representation for a list of headers
    * and redacts sensitive headers' values.
    *
    *  @param start       the starting string
    *  @param separator   the separator string
    *  @param end         the ending string
    *  @param redactWhen  the function for filtering out header values of sensitive headers
    *  @return            a string representation of the list of headers.
    *                      The resulting string begins with the string `start`
    *                      and ends with the string `end`. Inside, the string
    *                      representations of all headers are separated
    *                      by the string `separator`. Sensitive headers' values
    *                      are redacted with the `redactWhen` function.
    */
  def mkString(
      start: String,
      separator: String,
      end: String,
      redactWhen: CIString => Boolean,
  ): String =
    headers.iterator
      .map {
        case h if redactWhen(h.name) => Header.Raw.toString(h.name, "<REDACTED>")
        case h => Header.Raw.toString(h.name, h.value)
      }
      .mkString(start, separator, end)

  /** Creates a string representation for a list of headers
    * and redacts sensitive headers' values.
    *
    *  @param separator   the separator string
    *  @param redactWhen  the function for filtering out header values of sensitive headers
    *  @return            a string representation of the list of headers.
    *                      The resulting string is constructed from the string representations
    *                      of all headers separated by the string `separator`. Sensitive headers'
    *                      values are redacted with the `redactWhen` function.
    */
  def mkString(
      separator: String,
      redactWhen: CIString => Boolean,
  ): String =
    headers.iterator
      .map {
        case h if redactWhen(h.name) => Header.Raw.toString(h.name, "<REDACTED>")
        case h => Header.Raw.toString(h.name, h.value)
      }
      .mkString(separator)

  override def toString: String =
    this.show
}

object Headers {
  val empty: Headers = Headers(List.empty[Header.Raw])

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

  @deprecated("use Headers.apply", "0.22.0")
  def of(headers: Header.ToRaw*): Headers = apply(headers: _*)

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
    `Content-Length`.name,
    `Content-Range`.name,
    Trailer.name,
    `Transfer-Encoding`.name,
  )

  val SensitiveHeaders: Set[CIString] = Set(
    Authorization.name,
    Cookie.name,
    `Set-Cookie`.name,
  )
}
