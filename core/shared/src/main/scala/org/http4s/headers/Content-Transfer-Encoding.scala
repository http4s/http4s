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
import cats.parse.Parser.ignoreCase
import org.typelevel.ci._

/** This header specifies the way content is mime encoded.
  *
  * @see [[https://www.ietf.org/rfc/rfc2045.txt]]
  */
sealed abstract class `Content-Transfer-Encoding`(val value: CIString)
    extends Product
    with Serializable

object `Content-Transfer-Encoding` {
  case object `7bit` extends `Content-Transfer-Encoding`(ci"7bit")
  case object `8bit` extends `Content-Transfer-Encoding`(ci"8bit")
  case object Binary extends `Content-Transfer-Encoding`(ci"binary")
  case object QuotedPrintable extends `Content-Transfer-Encoding`(ci"quoted-printable")
  case object Base64 extends `Content-Transfer-Encoding`(ci"base64")
  case object IetfToken extends `Content-Transfer-Encoding`(ci"ietf-token")
  case object XToken extends `Content-Transfer-Encoding`(ci"x-token")

  private[http4s] val parser: Parser[`Content-Transfer-Encoding`] = {
    val sevenBitParser = ignoreCase("7bit").as(`7bit`)
    val eightBitParser = ignoreCase("8bit").as(`8bit`)
    val binaryParser = ignoreCase("binary").as(Binary)
    val quotedPrintableParser = ignoreCase("quoted-printable").as(QuotedPrintable)
    val base64Parser = ignoreCase("base64").as(Base64)
    val ietfTokenParser = ignoreCase("ietf-token").as(IetfToken)
    val xTokenParser = ignoreCase("x-token").as(XToken)
    sevenBitParser | eightBitParser | binaryParser | quotedPrintableParser | base64Parser | ietfTokenParser | xTokenParser
  }

  def parse(s: String): ParseResult[`Content-Transfer-Encoding`] =
    ParseResult.fromParser(parser, "Invalid Content-Transfer-Encoding header")(s)

  implicit val headerInstance: Header[`Content-Transfer-Encoding`, Header.Single] =
    Header.create(
      ci"Content-Transfer-Encoding",
      _.value.toString,
      parse,
    )
}
