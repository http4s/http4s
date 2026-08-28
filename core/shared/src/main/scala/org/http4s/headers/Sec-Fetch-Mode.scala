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

// https://w3c.github.io/webappsec-fetch-metadata/#sec-fetch-mode-header

abstract class `Sec-Fetch-Mode` private[headers] (val value: String)

object `Sec-Fetch-Mode` {
  case object cors extends `Sec-Fetch-Mode`("cors")
  case object navigate extends `Sec-Fetch-Mode`("navigate")
  case object `no-cors` extends `Sec-Fetch-Mode`("no-cors")
  case object `same-origin` extends `Sec-Fetch-Mode`("same-origin")
  case object websocket extends `Sec-Fetch-Mode`("websocket")

  private[http4s] val types: Map[String, `Sec-Fetch-Mode`] =
    List(
      cors,
      navigate,
      `no-cors`,
      `same-origin`,
      websocket,
    )
      .map(i => (i.value, i))
      .toMap

  private val parser: Parser[`Sec-Fetch-Mode`] =
    Parser.anyChar.rep.string.mapFilter(types.get)

  def parse(s: String): ParseResult[`Sec-Fetch-Mode`] =
    ParseResult.fromParser(parser, "Invalid Sec-Fetch-Mode header")(s)

  def apply(value: `Sec-Fetch-Mode`): `Sec-Fetch-Mode` =
    value

  implicit val headerInstance: Header[`Sec-Fetch-Mode`, Header.Single] =
    Header.createRendered(
      ci"Sec-Fetch-Mode",
      _.value,
      parse,
    )
}
