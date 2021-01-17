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

import cats.parse.Parser
import org.http4s
import org.http4s.EntityTag.{Strong, Weakness, parser => entityTagParser}
import org.http4s.util.Writer

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton {

  type EntityTag = http4s.EntityTag
  val EntityTag: http4s.EntityTag.type = http4s.EntityTag

  def apply(tag: String, weakness: Weakness = Strong): ETag =
    ETag(http4s.EntityTag(tag, weakness))

  override def parse(s: String): ParseResult[ETag] =
    ParseResult.fromParser(parser, "ETag header")(s)

  /* `ETag = entity-tag`
   *
   * @see [[https://tools.ietf.org/html/rfc7232#section-2.3]]
   */
  private[http4s] val parser: Parser[ETag] =
    entityTagParser.map(ETag.apply)
}

final case class ETag(tag: EntityTag) extends Header.Parsed {
  def key: ETag.type = ETag
  override def value: String = tag.toString()
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
