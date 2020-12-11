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

import org.http4s.headers._
import org.http4s.internal.parboiled2._

trait ProxyAuthenticateHeader {
  def PROXY_AUTHENTICATE(value: String): ParseResult[`Proxy-Authenticate`] =
    new ProxyAuthenticateParser(value).parse

  private class ProxyAuthenticateParser(input: ParserInput)
      extends ChallengeParser[`Proxy-Authenticate`](input) {
    def entry: Rule1[`Proxy-Authenticate`] =
      rule {
        oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { (xs: Seq[Challenge]) =>
          `Proxy-Authenticate`(xs.head, xs.tail: _*)
        }
      }
  }
}
