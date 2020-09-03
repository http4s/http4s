/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.{Eq, Eval, Foldable, Monoid, Show}
import cats.syntax.all._
import org.http4s.headers.`Set-Cookie`
import org.typelevel.ci.CIString
import scala.collection.mutable.ListBuffer

/** A collection of HTTP Headers */
final class Headers private (private val headers: List[Header]) extends AnyVal {
  def toList: List[Header] = headers

  def isEmpty: Boolean = headers.isEmpty

  def nonEmpty: Boolean = headers.nonEmpty

  def drop(n: Int): Headers = if (n == 0) this else new Headers(headers.drop(n))

  def iterator: Iterator[Header] = headers.iterator

  /** Attempt to get a [[org.http4s.Header]] of type key.HeaderT from this collection
    *
    * @param key [[HeaderKey.Extractable]] that can identify the required header
    * @return a scala.Option possibly containing the resulting header of type key.HeaderT
    * @see [[Header]] object and get([[org.typelevel.ci.CIString]])
    */
  def get(key: HeaderKey.Extractable): Option[key.HeaderT] = key.from(this)

  @deprecated(
    "Use response.cookies instead. Set-Cookie is unique among HTTP headers in that it can be repeated but can't be joined by a ','. This will return only the first Set-Cookie header. `response.cookies` will return the complete list.",
    "0.16.0-RC1"
  )
  def get(key: `Set-Cookie`.type): Option[`Set-Cookie`] =
    key.from(this).headOption

  /** Attempt to get a [[org.http4s.Header]] from this collection of headers
    *
    * @param key name of the header to find
    * @return a scala.Option possibly containing the resulting [[org.http4s.Header]]
    */
  def get(key: CIString): Option[Header] = headers.find(_.name == key)

  /** Make a new collection adding the specified headers, replacing existing headers of singleton type
    * The passed headers are assumed to contain no duplicate Singleton headers.
    *
    * @param in multiple [[Header]] to append to the new collection
    * @return a new [[Headers]] containing the sum of the initial and input headers
    */
  def put(in: Header*): Headers =
    if (in.isEmpty) this
    else if (this.isEmpty) new Headers(in.toList)
    else this ++ Headers(in.toList)

  /** Concatenate the two collections
    * If the resulting collection is of Headers type, duplicate Singleton headers will be removed from
    * this Headers collection.
    *
    * @param that collection to append
    * @tparam B type contained in collection `that`
    * @tparam That resulting type of the new collection
    */
  def ++(that: Headers): Headers =
    if (that.isEmpty) this
    else if (this.isEmpty) that
    else {
      val hs = that.toList
      val acc = new ListBuffer[Header]
      this.headers.foreach { orig =>
        orig.parsed match {
          case _: Header.Recurring => acc += orig
          case _: `Set-Cookie` => acc += orig
          case h if !hs.exists(_.name == h.name) => acc += orig
          case _ => // NOOP, drop non recurring header that already exists
        }
      }

      val h = new Headers(acc.prependToList(hs))
      h
    }

  def filterNot(f: Header => Boolean): Headers =
    Headers(headers.filterNot(f))

  def filter(f: Header => Boolean): Headers =
    Headers(headers.filter(f))

  def collectFirst[B](f: PartialFunction[Header, B]): Option[B] =
    headers.collectFirst(f)

  def foldMap[B: Monoid](f: Header => B): B =
    headers.foldMap(f)

  def foldLeft[A](z: A)(f: (A, Header) => A): A =
    headers.foldLeft(z)(f)

  def foldRight[A](z: Eval[A])(f: (Header, Eval[A]) => Eval[A]): Eval[A] =
    Foldable[List].foldRight(headers, z)(f)

  def foreach(f: Header => Unit): Unit =
    headers.foreach(f)

  def size: Int = headers.size

  /** Removes the `Content-Length`, `Content-Range`, `Trailer`, and
    * `Transfer-Encoding` headers.
    *
    *  https://tools.ietf.org/html/rfc7231#section-3.3
    */
  def removePayloadHeaders: Headers =
    filterNot(h => Headers.PayloadHeaderKeys(h.name))

  def redactSensitive(
      redactWhen: CIString => Boolean = Headers.SensitiveHeaders.contains): Headers =
    Headers(headers.map {
      case h if redactWhen(h.name) => Header.Raw(h.name, "<REDACTED>")
      case h => h
    })

  def exists(f: Header => Boolean): Boolean =
    headers.exists(f)

  def forall(f: Header => Boolean): Boolean =
    headers.forall(f)

  def find(f: Header => Boolean): Option[Header] =
    headers.find(f)

  def count(f: Header => Boolean): Int =
    headers.count(f)

  override def toString: String =
    Headers.headersShow.show(this)
}

object Headers {
  val empty = apply(List.empty)

  def of(headers: Header*): Headers =
    Headers(headers.toList)

  @deprecated("Use Headers.of", "0.20.0")
  def apply(headers: Header*): Headers =
    of(headers: _*)

  /** Create a new Headers collection from the headers */
  // def apply(headers: Header*): Headers = Headers(headers.toList)

  /** Create a new Headers collection from the headers */
  def apply(headers: List[Header]): Headers = new Headers(headers)

  implicit val headersShow: Show[Headers] =
    Show.show[Headers] {
      _.iterator.map(_.show).mkString("Headers(", ", ", ")")
    }

  implicit val HeadersEq: Eq[Headers] = Eq.by(_.toList)

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
