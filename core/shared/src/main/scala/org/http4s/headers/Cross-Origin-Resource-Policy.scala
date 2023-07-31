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

/** This response header conveys a desire that the browser blocks no-cors cross-origin/cross-site
  * requests to the given resource
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cross-Origin-Resource-Policy]]
  */
sealed abstract class `Cross-Origin-Resource-Policy`(val value: String)
    extends Product
    with Serializable

object `Cross-Origin-Resource-Policy` {
  case object SameSite extends `Cross-Origin-Resource-Policy`("same-site")
  case object SameOrigin extends `Cross-Origin-Resource-Policy`("same-origin")
  case object CrossOrigin extends `Cross-Origin-Resource-Policy`("cross-origin")

  private[http4s] val parser: Parser[`Cross-Origin-Resource-Policy`] = {
    val sameSiteParser = string("same-site").as(SameSite)
    val sameOriginParser = string("same-origin").as(SameOrigin)
    val crossOriginParser = string("cross-origin").as(CrossOrigin)
    sameSiteParser | sameOriginParser | crossOriginParser
  }

  def parse(s: String): ParseResult[`Cross-Origin-Resource-Policy`] =
    ParseResult.fromParser(parser, "Invalid Cross-Origin-Resource-Policy header")(s)

  implicit val headerInstance: Header[`Cross-Origin-Resource-Policy`, Header.Single] =
    Header.create(
      ci"Cross-Origin-Resource-Policy",
      _.value,
      parse,
    )
}
