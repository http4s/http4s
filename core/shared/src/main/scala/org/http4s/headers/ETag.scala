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
import org.http4s.EntityTag.Strong
import org.http4s.EntityTag.Weakness
import org.http4s.EntityTag.{parser => entityTagParser}
import org.typelevel.ci._

object ETag {

  type EntityTag = http4s.EntityTag
  val EntityTag: http4s.EntityTag.type = http4s.EntityTag

  def apply(tag: String, weakness: Weakness = Strong): ETag =
    ETag(http4s.EntityTag(tag, weakness))

  def parse(s: String): ParseResult[ETag] =
    ParseResult.fromParser(parser, "Invalid ETag header")(s)

  /** `ETag = entity-tag`
    *
    * @see [[https://datatracker.ietf.org/doc/html/rfc7232#section-2.3]]
    */
  private[http4s] val parser: Parser[ETag] =
    entityTagParser.map(ETag.apply)

  implicit val headerInstance: Header[ETag, Header.Single] =
    Header.createRendered(
      ci"ETag",
      _.tag,
      parse,
    )
}

final case class ETag(tag: EntityTag)
