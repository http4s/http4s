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
import cats.parse.Parser.string
import org.typelevel.ci._

/** That response header allows you to ensure a top-level document does not share a browsing context group with cross-origin documents.
  *
  * @see [[https://html.spec.whatwg.org/multipage/origin.html#cross-origin-opener-policies Cross-origin opener policies]]
  */
sealed abstract class `Cross-Origin-Opener-Policy`(val value: String)
    extends Product
    with Serializable

object `Cross-Origin-Opener-Policy` {
  case object UnsafeNone extends `Cross-Origin-Opener-Policy`("unsafe-none")
  case object SameOriginAllowPopups extends `Cross-Origin-Opener-Policy`("same-origin-allow-popups")
  case object SameOrigin extends `Cross-Origin-Opener-Policy`("same-origin")

  private[http4s] val parser: Parser[`Cross-Origin-Opener-Policy`] = {
    val unsafeNoneParser = string("unsafe-none").as(UnsafeNone)
    val sameOriginAllowPopupsParser = string("same-origin-allow-popups").as(SameOriginAllowPopups)
    val sameOriginParser = string("same-origin").as(SameOrigin)

    unsafeNoneParser | sameOriginAllowPopupsParser | sameOriginParser
  }

  def parse(s: String): ParseResult[`Cross-Origin-Opener-Policy`] =
    ParseResult.fromParser(parser, "Invalid Cross-Origin-Opener-Policy header")(s)

  implicit val headerInstance: Header[`Cross-Origin-Opener-Policy`, Header.Single] =
    Header.create(
      ci"Cross-Origin-Opener-Policy",
      _.value,
      parse,
    )
}
