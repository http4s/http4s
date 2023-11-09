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
import cats.parse.Parser
import org.http4s.internal.parsing.CommonRules
import org.typelevel.ci.CIString
import org.typelevel.ci.CIStringSyntax

/** The X-Frame-Options HTTP response header can be used to indicate whether or not a browser should be allowed to
  * render a page in a frame or iframe.
  *
  * [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Frame-Options]]
  *
  * @param value can be `DENY` or `SAMEORIGIN`.
  */
sealed abstract class `X-Frame-Options` private (private val value: CIString)

object `X-Frame-Options` extends HeaderCompanion[`X-Frame-Options`]("X-Frame-Options") {

  object deny extends `X-Frame-Options`(ci"DENY")
  object sameOrigin extends `X-Frame-Options`(ci"SAMEORIGIN")

  private[http4s] val parser: Parser[`X-Frame-Options`] =
    CommonRules.headerRep1(CommonRules.quotedString).mapFilter {
      case NonEmptyList(head, _) if head.equalsIgnoreCase("deny") => Some(deny)
      case NonEmptyList(head, _) if head.equalsIgnoreCase("sameorigin") => Some(sameOrigin)
      case _ => None
    }

  override def parse(s: String): ParseResult[`X-Frame-Options`] =
    ParseResult.fromParser(parser, "Invalid X-Frame-Options header")(s)

  implicit val headerInstance: Header[`X-Frame-Options`, Header.Single] =
    Header.createRendered(
      ci"X-Frame-Options",
      _.value,
      parse,
    )
}
