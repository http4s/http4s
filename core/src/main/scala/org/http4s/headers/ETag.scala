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

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer
import cats.Show
import org.http4s.headers.ETag.EntityTag.{Strong, Weakness}

object ETag extends HeaderKey.Internal[ETag] with HeaderKey.Singleton {
  final case class EntityTag(tag: String, weakness: Weakness = Strong) {
    override def toString(): String =
      weakness match {
        case EntityTag.Weak   => "W/\"" + tag + '"'
        case EntityTag.Strong => "\"" + tag + '"'
      }
  }

  object EntityTag {
    implicit val http4sShowForEntityTag: Show[EntityTag] =
      Show.fromToString

    sealed trait Weakness extends Product with Serializable
    case object Weak extends Weakness
    case object Strong extends Weakness
  }

  def apply(tag: String, weakness: Weakness = Strong): ETag =
    ETag(EntityTag(tag, weakness))

  override def parse(s: String): ParseResult[ETag] =
    HttpHeaderParser.ETAG(s)
}

final case class ETag(tag: ETag.EntityTag) extends Header.Parsed {
  def key: ETag.type = ETag
  override def value: String = tag.toString()
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
