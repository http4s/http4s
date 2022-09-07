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

object `If-Modified-Since` {
  def parse(s: String): ParseResult[`If-Modified-Since`] =
    ParseResult.fromParser(parser, "Invalid If-Modified-Since header")(s)

  /* `If-Modified-Since = HTTP-date` */
  private[http4s] val parser: Parser[`If-Modified-Since`] =
    HttpDate.parser.map(apply)

  implicit val headerInstance: Header[`If-Modified-Since`, Header.Single] =
    Header.createRendered(
      ci"If-Modified-Since",
      _.date,
      parse,
    )

}

/** {{
  *   The "If-Modified-Since" header field makes a GET or HEAD request
  *   method conditional on the selected representation's modification date
  *   being more recent than the date provided in the field-value.
  * }}
  *
  * [[https://datatracker.ietf.org/doc/html/rfc7232#section-3.3 RFC-7232]]
  */
final case class `If-Modified-Since`(date: HttpDate)
