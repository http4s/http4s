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
import cats.parse._
import cats.syntax.foldable._
import org.http4s.internal.parsing.CommonRules
import org.typelevel.ci._

/** {{{
  *  The "If-None-Match" header field makes the request method conditional
  *  on a recipient cache or origin server either not having any current
  *  representation of the target resource, when the field-value is "*",
  *  or having a selected representation with an entity-tag that does not
  *  match any of those listed in the field-value.
  * }}}
  *
  * From [[https://datatracker.ietf.org/doc/html/rfc7232#section-3.2 RFC-7232]]
  */
object `If-None-Match` {

  /** Match any existing entity */
  val `*`: `If-None-Match` = `If-None-Match`(None)

  def apply(first: EntityTag, rest: EntityTag*): `If-None-Match` =
    `If-None-Match`(Some(NonEmptyList.of(first, rest: _*)))

  def parse(s: String): ParseResult[`If-None-Match`] =
    ParseResult.fromParser(parser, "Invalid If-None-Match header")(s)

  private[http4s] val parser = Parser
    .string("*")
    .as(`*`)
    .orElse(CommonRules.headerRep1(EntityTag.parser).map { tags =>
      `If-None-Match`(Some(tags))
    })

  implicit val headerInstance: Header[`If-None-Match`, Header.Single] =
    Header.create(
      ci"If-None-Match",
      _.tags match {
        case None => "*"
        case Some(tags) => tags.mkString_("", ",", "")
      },
      parse,
    )
}

final case class `If-None-Match`(tags: Option[NonEmptyList[EntityTag]])
