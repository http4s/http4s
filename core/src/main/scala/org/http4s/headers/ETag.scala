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

import cats.parse.{Parser, Parser1, Rfc5234}
import cats.Show
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Writer

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton {
  final case class EntityTag(tag: String, weak: Boolean = false) {
    override def toString() =
      if (weak) "W/\"" + tag + '"'
      else "\"" + tag + '"'
  }

  object EntityTag {
    implicit val http4sShowForEntityTag: Show[EntityTag] =
      Show.fromToString
  }

  def apply(tag: String, weak: Boolean = false): ETag = ETag(EntityTag(tag, weak))

  override def parse(s: String): ParseResult[ETag] =
    ParseResult.fromParser(parser, "ETag header")(s)

  /* `ETag = entity-tag`
   *
   * @see [[https://tools.ietf.org/html/rfc7232#section-2.3]]
   */
  private[http4s] val parser: Parser1[ETag] = {
    import Parser.{charIn, string1}
    import Rfc5234.{dquote}
    import Rfc7230.{obsText}

    // weak       = %x57.2F ; "W/", case-sensitive
    val weak = string1("W/").as(true)

    // etagc      = %x21 / %x23-7E / obs-text
    //            ; VCHAR except double quotes, plus obs-text
    val etagc = charIn(0x21.toChar, 0x23.toChar to 0x7e.toChar: _*).orElse1(obsText)

    // opaque-tag = DQUOTE *etagc DQUOTE
    val opaqueTag = dquote *> etagc.rep.string <* dquote

    // entity-tag = [ weak ] opaque-tag
    val entityTag = (weak.?.with1 ~ opaqueTag).map { case (weakness, tag) =>
      new EntityTag(tag, weakness.getOrElse(false))
    }

    entityTag.map(ETag.apply)
  }
}

final case class ETag(tag: ETag.EntityTag) extends Header.Parsed {
  def key: ETag.type = ETag
  override def value: String = tag.toString()
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
