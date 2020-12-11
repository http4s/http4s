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
package headers

import cats.data.NonEmptyList
import cats.syntax.foldable._
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `If-Match` extends HeaderKey.Internal[`If-Match`] with HeaderKey.Singleton {

  /** Match any existing entity */
  val `*` = `If-Match`(None)

  def apply(first: ETag.EntityTag, rest: ETag.EntityTag*): `If-Match` =
    `If-Match`(Some(NonEmptyList.of(first, rest: _*)))

  override def parse(s: String): ParseResult[`If-Match`] =
    HttpHeaderParser.IF_MATCH(s)
}

/** Request header to make the request conditional on the current contents of the origin server
  * at the given target resource (URI).
  *
  * [[https://tools.ietf.org/html/rfc7232#section-3.1 RFC-7232 Section 3.1]]
  */
final case class `If-Match`(tags: Option[NonEmptyList[ETag.EntityTag]]) extends Header.Parsed {
  override def key: `If-Match`.type = `If-Match`
  override def value: String =
    tags match {
      case None => "*"
      case Some(tags) => tags.mkString_("", ",", "")
    }
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
