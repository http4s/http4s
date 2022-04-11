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

/** That response header prevents a document from loading any cross-origin resources that don't explicitly grant the document permission (using CORP or CORS)
  *
  * @see [[https://html.spec.whatwg.org/multipage/origin.html#coep Cross-origin embedder policies]]
  */
sealed abstract class `Cross-Origin-Embedder-Policy`(val value: String)
    extends Product
    with Serializable

object `Cross-Origin-Embedder-Policy` {
  case object UnsafeNone extends `Cross-Origin-Embedder-Policy`("unsafe-none")
  case object RequireCorp extends `Cross-Origin-Embedder-Policy`("require-corp")

  private[http4s] val parser: Parser[`Cross-Origin-Embedder-Policy`] = {
    val unsafeNoneParser = string("unsafe-none").as(UnsafeNone)
    val requireCorpParser = string("require-corp").as(RequireCorp)

    unsafeNoneParser | requireCorpParser
  }

  def parse(s: String): ParseResult[`Cross-Origin-Embedder-Policy`] =
    ParseResult.fromParser(parser, "Invalid Cross-Origin-Embedder-Policy header")(s)

  implicit val headerInstance: Header[`Cross-Origin-Embedder-Policy`, Header.Single] =
    Header.create(
      ci"Cross-Origin-Embedder-Policy",
      _.value,
      parse,
    )
}
