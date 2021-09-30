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
import cats.parse.Parser.{char, string}
import org.typelevel.ci._

object DNT {

//Parses '0', '1', or null into Option[Boolean]
  private val rawParser: Parser[Option[Boolean]] = {
    val nullParser = string("null").as(None)
    val falseParser = char('0').as(Some(false))
    val trueParser = char('1').as(Some(true))
    (falseParser | trueParser | nullParser)
  }

  def parse(s: String): ParseResult[DNT] =
    ParseResult.fromParser(parser, "Invalid DNT header")(s)

  /*
   * `DNT = 0 | 1 | null`
   */
  private[http4s] val parser: Parser[DNT] =
    rawParser.map(DNT.apply)

  implicit val headerInstance: Header[DNT, Header.Single] =
    Header.create(
      ci"DNT",
      _.renderString,
      parse
    )
}

final case class DNT(value: Option[Boolean]) {
  private def renderString = value match {
    case Some(false) => "0"
    case Some(true) => "1"
    case None => "null"
  }
}
