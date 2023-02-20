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
import org.typelevel.ci.CIStringSyntax

sealed trait `X-Content-Type-Options`

object `X-Content-Type-Options`
    extends HeaderCompanion[`X-Content-Type-Options`]("X-Content-Type-Options") {

  case object nosniff extends `X-Content-Type-Options`

  override def parse(s: String): ParseResult[`X-Content-Type-Options`] =
    ParseResult.fromParser(parser, "Invalid X-Content-Type-Options header")(s)

  private[http4s] val parser: Parser[`X-Content-Type-Options`] =
    CommonRules.headerRep1(CommonRules.quotedString).mapFilter {
      case NonEmptyList(head, _) if head.equalsIgnoreCase("nosniff") => Some(nosniff)
      case _ => None
    }

  override implicit val headerInstance: Header[`X-Content-Type-Options`, Header.Single] =
    Header.createRendered(
      ci"X-Content-Type-Options",
      _ => ci"nosniff",
      parse,
    )
}
