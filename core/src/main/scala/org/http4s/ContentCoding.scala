/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpEncoding.scala
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

import cats.{Order, Show}
import cats.syntax.eq._
import cats.instances.tuple._
import cats.instances.string._
import org.http4s.internal.parboiled2.{Parser => PbParser, _}
import org.http4s.parser.Http4sParser
import org.http4s.util._
import org.http4s.QValue.QValueParser
import scala.util.hashing.MurmurHash3

class ContentCoding(val coding: String, override val qValue: QValue = QValue.One)
    extends HasQValue
    with Ordered[ContentCoding]
    with Renderable {
  def withQValue(q: QValue): ContentCoding = ContentCoding(coding, q)

  @deprecated("Use `Accept-Encoding`.isSatisfiedBy(encoding)", "0.16.1")
  def satisfies(encoding: ContentCoding): Boolean = encoding.satisfiedBy(this)

  @deprecated("Use `Accept-Encoding`.isSatisfiedBy(encoding)", "0.16.1")
  def satisfiedBy(encoding: ContentCoding): Boolean =
    (this === ContentCoding.`*` || this.coding.equalsIgnoreCase(encoding.coding)) &&
      qValue.isAcceptable && encoding.qValue.isAcceptable

  def matches(encoding: ContentCoding): Boolean =
    (this === ContentCoding.`*` || this.coding.equalsIgnoreCase(encoding.coding))

  override def equals(o: Any) = o match {
    case that: ContentCoding => this.coding.equalsIgnoreCase(that.coding) && this.qValue === that.qValue
    case _ => false
  }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0) {
      hash = MurmurHash3.mixLast(coding.toLowerCase.##, qValue.##)
    }
    hash
  }

  override def toString = s"ContentCoding(${coding.toLowerCase}, $qValue)"

  override def compare(other: ContentCoding): Int =
    ContentCoding.order.compare(this, other)

  override def render(writer: Writer): writer.type = writer << coding.toLowerCase << qValue
}

object ContentCoding {
  def apply(coding: String): ContentCoding = new ContentCoding(coding)
  def apply(coding: String, qValue: QValue): ContentCoding = new ContentCoding(coding, qValue)

  val `*` : ContentCoding = ContentCoding("*")

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-1
  val compress = ContentCoding("compress")
  val deflate = ContentCoding("deflate")
  val exi = ContentCoding("exi")
  val gzip = ContentCoding("gzip")
  val identity = ContentCoding("identity")
  val `pack200-gzip` = ContentCoding("pack200-gzip")

  // Legacy encodings defined by RFC2616 3.5.
  val `x-compress` = compress
  val `x-gzip` = gzip

  def standard: Map[String, ContentCoding] =
    List(`*`, compress, deflate, exi, gzip, identity, `pack200-gzip`).map(c => c.coding -> c).toMap

  /**
    * Parse a Transfer Coding
    */
  def parse(s: String): ParseResult[ContentCoding] =
    new Http4sParser[ContentCoding](s, "Invalid Content Coding") with ContentCodingParser {
      def main = EncodingRangeDecl
    }.parse

  private[http4s] trait ContentCodingParser extends QValueParser { self: PbParser =>

    def EncodingRangeDecl: Rule1[ContentCoding] = rule {
      (EncodingRangeDef ~ QualityValue) ~> { (coding: ContentCoding, q: QValue) =>
        if (q == org.http4s.QValue.One) coding
        else coding.withQValue(q)
      }
    }

    def EncodingRangeDef: Rule1[ContentCoding] = rule {
      "*" ~ push(ContentCoding.`*`) | Token ~> { s: String =>
        ContentCoding.standard.getOrElse(s, ContentCoding(s))
      }
    }
  }

  implicit val http4sInstancesForContentCoding: Show[ContentCoding] with HttpCodec[ContentCoding] =
    new Show[ContentCoding] with HttpCodec[ContentCoding] {
      override def show(s: ContentCoding): String = s.toString

      override def parse(s: String): ParseResult[ContentCoding] =
        ContentCoding.parse(s)

      override def render(writer: Writer, coding: ContentCoding): writer.type =
        writer << coding.coding.toLowerCase << coding.qValue

    }

  implicit val order: Order[ContentCoding] = Order.by(c => (c.coding.toLowerCase, c.qValue))

}
