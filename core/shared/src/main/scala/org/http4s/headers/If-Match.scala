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

object `If-Match` {

  /** Match any existing entity */
  val `*`: `If-Match` = `If-Match`(None)

  def apply(first: EntityTag, rest: EntityTag*): `If-Match` =
    `If-Match`(Some(NonEmptyList.of(first, rest: _*)))

  def parse(s: String): ParseResult[`If-Match`] =
    ParseResult.fromParser(parser, "Invalid If-Match header")(s)

  private[http4s] val parser = Parser
    .string("*")
    .as(`*`)
    .orElse(CommonRules.headerRep1(EntityTag.parser).map { tags =>
      `If-Match`(Some(tags))
    })

  implicit val headerInstance: Header[`If-Match`, Header.Single] =
    Header.create(
      ci"If-Match",
      _.tags match {
        case None => "*"
        case Some(nel) => nel.mkString_("", ",", "")

      },
      parse,
    )

}

/** Request header to make the request conditional on the current contents of the origin server
  * at the given target resource (URI).
  *
  * [[https://datatracker.ietf.org/doc/html/rfc7232#section-3.1 RFC-7232 Section 3.1]]
  */
final case class `If-Match`(tags: Option[NonEmptyList[EntityTag]])
