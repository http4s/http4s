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

import cats.Show
import cats.parse.Parser
import cats.parse.Rfc5234
import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderable
import org.http4s.util.Writer

final case class EntityTag(tag: String, weakness: EntityTag.Weakness = EntityTag.Strong)
    extends Renderable {
  override def toString(): String =
    weakness match {
      case EntityTag.Weak => "W/\"" + tag + '"'
      case EntityTag.Strong => "\"" + tag + '"'
    }

  override def render(writer: Writer): writer.type =
    writer << toString()
}

object EntityTag {
  implicit val http4sShowForEntityTag: Show[EntityTag] =
    Show.fromToString

  sealed trait Weakness extends Product with Serializable
  case object Weak extends Weakness
  case object Strong extends Weakness

  /*
   * entity-tag = [ weak ] opaque-tag
   *
   * @see [[https://datatracker.ietf.org/doc/html/rfc7232#section-2.3]]
   */
  private[http4s] val parser: Parser[EntityTag] = {
    import Parser.{charIn, string}
    import Rfc5234.dquote
    import CommonRules.obsText

    // weak       = %x57.2F ; "W/", case-sensitive
    val weak = string("W/").as(EntityTag.Weak)

    // etagc      = %x21 / %x23-7E / obs-text
    //            ; VCHAR except double quotes, plus obs-text
    val etagc = charIn(0x21.toChar, 0x23.toChar to 0x7e.toChar: _*).orElse(obsText)

    // opaque-tag = DQUOTE *etagc DQUOTE
    val opaqueTag = dquote *> etagc.rep0.string <* dquote

    (weak.?.with1 ~ opaqueTag).map { case (weakness, tag) =>
      new EntityTag(tag, weakness.getOrElse(EntityTag.Strong))
    }
  }
}
