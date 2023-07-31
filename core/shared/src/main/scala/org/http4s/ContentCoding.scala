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

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpEncoding.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import cats.Order
import cats.Show
import cats.parse.Parser
import cats.syntax.all._
import org.http4s.util._

import java.{util => ju}
import scala.util.hashing.MurmurHash3

class ContentCoding private (val coding: String, override val qValue: QValue = QValue.One)
    extends HasQValue
    with Ordered[ContentCoding]
    with Renderable {
  def withQValue(q: QValue): ContentCoding = new ContentCoding(coding, q)

  def matches(encoding: ContentCoding): Boolean =
    this === ContentCoding.`*` || this.coding.equalsIgnoreCase(encoding.coding)

  override def equals(o: Any): Boolean =
    o match {
      case that: ContentCoding =>
        this.coding.equalsIgnoreCase(that.coding) && this.qValue === that.qValue
      case _ => false
    }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0)
      hash = MurmurHash3.mixLast(coding.toLowerCase.##, qValue.##)
    hash
  }

  override def toString: String = s"ContentCoding(${coding.toLowerCase}, $qValue)"

  override def compare(other: ContentCoding): Int =
    ContentCoding.http4sOrderForContentCoding.compare(this, other)

  override def render(writer: Writer): writer.type =
    ContentCoding.http4sHttpCodecForContentCoding.render(writer, this)
}

object ContentCoding {
  def unsafeFromString(coding: String): ContentCoding =
    fromString(coding).valueOr(throw _)

  def fromString(coding: String): ParseResult[ContentCoding] =
    parse(coding)

  val `*` : ContentCoding = new ContentCoding("*")

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-1
  val aes128gcm = new ContentCoding("aes128gcm")
  val br = new ContentCoding("br")
  val compress = new ContentCoding("compress")
  val deflate = new ContentCoding("deflate")
  val exi = new ContentCoding("exi")
  val gzip = new ContentCoding("gzip")
  val identity = new ContentCoding("identity")
  val `pack200-gzip` = new ContentCoding("pack200-gzip")
  val zstd = new ContentCoding("zstd")

  // Legacy encodings defined by RFC2616 3.5.
  val `x-compress`: ContentCoding = compress
  val `x-gzip`: ContentCoding = gzip

  val standard: Map[String, ContentCoding] =
    List(`*`, aes128gcm, br, compress, deflate, exi, gzip, identity, `pack200-gzip`, zstd)
      .map(c => c.coding -> c)
      .toMap

  private[http4s] val parser: Parser[ContentCoding] = {
    import org.http4s.internal.parsing.CommonRules.token

    val contentCoding = token.map(s => ContentCoding.standard.getOrElse(s, new ContentCoding(s)))

    (contentCoding ~ QValue.parser).map { case (coding, q) =>
      if (q === QValue.One) coding
      else coding.withQValue(q)
    }
  }

  /** Parse a Content Coding
    */
  def parse(s: String): ParseResult[ContentCoding] =
    ParseResult.fromParser(parser, "content coding")(s)

  implicit val http4sOrderForContentCoding: Order[ContentCoding] =
    Order.by(c => (c.coding.toLowerCase(ju.Locale.ENGLISH), c.qValue))

  implicit val http4sShowForContentCoding: Show[ContentCoding] =
    Show.fromToString
  implicit val http4sHttpCodecForContentCoding: HttpCodec[ContentCoding] =
    new HttpCodec[ContentCoding] {
      override def parse(s: String): ParseResult[ContentCoding] =
        ContentCoding.parse(s)

      override def render(writer: Writer, coding: ContentCoding): writer.type =
        writer << coding.coding.toLowerCase << coding.qValue
    }
}
