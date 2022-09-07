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
import org.typelevel.ci._

object Date {
  def parse(s: String): ParseResult[Date] =
    ParseResult.fromParser(parser, "Invalid Date header")(s)

  /* `Date = HTTP-date` */
  private[http4s] val parser: Parser[`Date`] =
    HttpDate.parser.map(apply)

  implicit val headerInstance: Header[Date, Header.Single] =
    Header.createRendered(
      ci"Date",
      _.date,
      parse,
    )
}

final case class Date(date: HttpDate)
