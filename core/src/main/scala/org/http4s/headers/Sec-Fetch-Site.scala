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

import cats.parse.{Parser, Rfc5234}
import org.typelevel.ci._
import org.http4s.internal.parsing.Rfc7230

// https://w3c.github.io/webappsec-fetch-metadata/#sec-fetch-site-header

sealed abstract class `Sec-Fetch-Site`(val value: String)

object `Sec-Fetch-Site` {
  case object `cross-site` extends `Sec-Fetch-Site`("cross-site")
  case object `same-origin` extends `Sec-Fetch-Site`("same-origin")
  case object `same-site` extends `Sec-Fetch-Site`("same-site")
  case object none extends `Sec-Fetch-Site`("none")

  private[http4s] val types: Map[String, `Sec-Fetch-Site`] =
    List(
      `cross-site`,
      `same-origin`,
      `same-site`,
      none
    )
      .map(i => (i.value.toLowerCase, i))
      .toMap

  // https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-header-structure#section-3.3.4

  private def token: Parser[String] = {
    import Rfc5234.alpha, Rfc7230.tchar, Parser.char
    val head = alpha.orElse(char('*')).string
    val tail = tchar.orElse(char(':')).orElse(char('/')).rep0.string
    (head ~ tail).map(t => t._1 + t._2)
  }

  private val parser: Parser[`Sec-Fetch-Site`] =
    token.mapFilter(s => types.get(s.toLowerCase))

  def parse(s: String): ParseResult[`Sec-Fetch-Site`] =
    ParseResult.fromParser(parser, "Invalid Sec-Fetch-Site header")(s)

  def apply(value: `Sec-Fetch-Site`): `Sec-Fetch-Site` =
    value

  implicit val headerInstance: Header[`Sec-Fetch-Site`, Header.Single] =
    Header.createRendered(
      ci"Sec-Fetch-Site",
      _.value,
      parse
    )
}
