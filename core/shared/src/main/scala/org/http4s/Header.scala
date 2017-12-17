/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpHeader.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s

import cats._
import cats.data.NonEmptyList
import cats.implicits.{catsSyntaxEither => _, _}
import org.http4s.syntax.string._
import org.http4s.util._
import scala.util.hashing.MurmurHash3

/**
  * Abstract representation o the HTTP header
  * @see org.http4s.HeaderKey
  */
sealed trait Header extends Renderable with Product {
  import Header.Raw

  def name: CaseInsensitiveString

  def parsed: Header

  def renderValue(writer: Writer): writer.type

  def value: String = {
    val w = new StringWriter
    renderValue(w).result
  }

  def is(key: HeaderKey): Boolean = key.matchHeader(this).isDefined

  def isNot(key: HeaderKey): Boolean = !is(key)

  override def toString: String = name + ": " + value

  def toRaw: Raw = Raw(name, value)

  final def render(writer: Writer): writer.type = {
    writer << name << ':' << ' '
    renderValue(writer)
  }

  final override def hashCode(): Int =
    MurmurHash3.mixLast(name.hashCode, MurmurHash3.productHash(parsed))

  final override def equals(that: Any): Boolean = that match {
    case h: AnyRef if this eq h => true
    case h: Header =>
      (name == h.name) &&
        (parsed.productArity == h.parsed.productArity) &&
        (parsed.productIterator.sameElements(h.parsed.productIterator))
    case _ => false
  }
}

object Header {
  def unapply(header: Header): Option[(CaseInsensitiveString, String)] =
    Some((header.name, header.value))

  def apply(name: String, value: String): Raw = Raw(name.ci, value)

  /**
    * Raw representation of the Header
    *
    * This can be considered the simplest representation where the header is specified as the product of
    * a key and a value
    * @param name case-insensitive string used to identify the header
    * @param value String representation of the header value
    */
  final case class Raw(name: CaseInsensitiveString, override val value: String) extends Header {
    private[this] var _parsed: Header = null
    final override def parsed: Header = {
      if (_parsed == null) {
        _parsed = parser.HttpHeaderParser.parseHeader(this).getOrElse(this)
      }
      _parsed
    }
    override def renderValue(writer: Writer): writer.type = writer.append(value)
  }

  /** A Header that is already parsed from its String representation. */
  trait Parsed extends Header {
    def key: HeaderKey
    def name: CaseInsensitiveString = key.name
    def parsed: this.type = this
  }

  /**
    * A recurring header that satisfies this clause of the Spec:
    *
    * Multiple message-header fields with the same field-name MAY be present in a message if and only if the entire
    * field-value for that header field is defined as a comma-separated list [i.e., #(values)]. It MUST be possible
    * to combine the multiple header fields into one "field-name: field-value" pair, without changing the semantics
    * of the message, by appending each subsequent field-value to the first, each separated by a comma.
    */
  trait Recurring extends Parsed {
    type Value
    def values: NonEmptyList[Value]
  }

  /** Simple helper trait that provides a default way of rendering the value */
  trait RecurringRenderable extends Recurring {
    type Value <: Renderable
    override def renderValue(writer: Writer): writer.type = {
      values.head.render(writer)
      values.tail.foreach(writer << ", " << _)
      writer
    }
  }

  implicit val HeaderShow: Show[Header] = Show.show[Header] {
    _.toString
  }

  implicit val HeaderEq: Eq[Header] = Eq.instance[Header] { (a, b) =>
    a.name === b.name && a.value === b.value
  }

}
