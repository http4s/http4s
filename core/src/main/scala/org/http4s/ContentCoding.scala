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

import org.http4s.syntax.string._
import org.http4s.util._

final case class ContentCoding(coding: CaseInsensitiveString, qValue: QValue = QValue.One)
    extends HasQValue
    with Renderable {
  def withQValue(q: QValue): ContentCoding = copy(coding, q)

  @deprecated("Use `Accept-Encoding`.isSatisfiedBy(encoding)", "0.16.1")
  def satisfies(encoding: ContentCoding): Boolean = encoding.satisfiedBy(this)

  @deprecated("Use `Accept-Encoding`.isSatisfiedBy(encoding)", "0.16.1")
  def satisfiedBy(encoding: ContentCoding): Boolean =
    (this.coding.toString == "*" || this.coding == encoding.coding) &&
      qValue.isAcceptable && encoding.qValue.isAcceptable

  def matches(encoding: ContentCoding): Boolean =
    (this.coding.toString == "*" || this.coding == encoding.coding)

  override def render(writer: Writer): writer.type = writer << coding << qValue

  // We want the normal case class generated methods except copy
  private def copy(coding: CaseInsensitiveString, q: QValue) =
    ContentCoding(coding, q)
}

object ContentCoding extends Registry {
  type Key = CaseInsensitiveString
  type Value = ContentCoding

  implicit def fromKey(k: CaseInsensitiveString): ContentCoding = ContentCoding(k)

  implicit def fromValue(v: ContentCoding): CaseInsensitiveString = v.coding

  val `*` : ContentCoding = registerKey("*".ci)

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-1
  val compress = registerKey("compress".ci)
  val deflate = registerKey("deflate".ci)
  val exi = registerKey("exi".ci)
  val gzip = registerKey("gzip".ci)
  val identity = registerKey("identity".ci)
  val `pack200-gzip` = registerKey("pack200-gzip".ci)

  // Legacy encodings defined by RFC2616 3.5.
  val `x-compress` = register("x-compress".ci, compress)
  val `x-gzip` = register("x-gzip".ci, gzip)

  def registered: Iterable[ContentCoding] =
    registry.snapshot.values
}
