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
import cats.parse.Parser.char
import cats.parse.Parser.string
import org.typelevel.ci._

sealed abstract class DNT(val value: String) extends Product with Serializable

object DNT {
  case object AllowTracking extends DNT("0")
  case object DisallowTracking extends DNT("1")
  case object NoPreference extends DNT("null")

  /*
   * `DNT = 0 | 1 | null`
   */
  private[http4s] val parser: Parser[DNT] = {
    val nullParser = string("null").as(NoPreference)
    val allowTrackingParser = char('0').as(AllowTracking)
    val disallowTrackingParser = char('1').as(DisallowTracking)
    allowTrackingParser | disallowTrackingParser | nullParser
  }

  def parse(s: String): ParseResult[DNT] =
    ParseResult.fromParser(parser, "Invalid DNT header")(s)

  implicit val headerInstance: Header[DNT, Header.Single] =
    Header.create(
      ci"DNT",
      _.value,
      parse,
    )
}
