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
package parser

import org.http4s.headers.`Strict-Transport-Security`
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.support.{::, HNil}

private[parser] trait StrictTransportSecurityHeader {
  def STRICT_TRANSPORT_SECURITY(value: String): ParseResult[`Strict-Transport-Security`] =
    StrictTransportSecurityParser(value).parse

  private case class StrictTransportSecurityParser(override val input: ParserInput)
      extends Http4sHeaderParser[`Strict-Transport-Security`](input) {
    def entry: Rule1[`Strict-Transport-Security`] =
      rule {
        maxAge ~ zeroOrMore(";" ~ OptWS ~ stsAttributes) ~ EOI
      }

    def maxAge: Rule1[`Strict-Transport-Security`] =
      rule {
        "max-age=" ~ Digits ~> { (age: String) =>
          `Strict-Transport-Security`
            .unsafeFromLong(maxAge = age.toLong, includeSubDomains = false, preload = false)
        }
      }

    def stsAttributes
        : Rule[`Strict-Transport-Security` :: HNil, `Strict-Transport-Security` :: HNil] =
      rule {
        "includeSubDomains" ~ MATCH ~> { (sts: `Strict-Transport-Security`) =>
          sts.withIncludeSubDomains(true)
        } |
          "preload" ~ MATCH ~> { (sts: `Strict-Transport-Security`) =>
            sts.withPreload(true)
          }
      }
  }
}
