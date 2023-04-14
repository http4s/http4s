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

/** Deprecation Header
  *
  * https://greenbytes.de/tech/webdav/draft-ietf-httpapi-deprecation-header-latest.html#the-deprecation-http-response-header-field
  */
object Deprecation {
  def parse(s: String): ParseResult[Deprecation] =
    ParseResult.fromParser(parser, "Invalid Deprecation header")(s)

  /* `Date = HTTP-date` */
  private[http4s] val parser: Parser[`Deprecation`] =
    HttpDate.imfFixdate.map(apply)

  implicit val headerInstance: Header[Deprecation, Header.Single] =
    Header.createRendered(
      ci"Deprecation",
      _.date,
      parse,
    )
}

final case class Deprecation(date: HttpDate)
