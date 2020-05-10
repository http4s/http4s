/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/WwwAuthenticateHeader.scala
 *
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s
package parser

import org.http4s.headers.`WWW-Authenticate`
import org.http4s.internal.parboiled2.{ParserInput, Rule1}

private[parser] trait WwwAuthenticateHeader {
  def WWW_AUTHENTICATE(value: String): ParseResult[`WWW-Authenticate`] =
    new WWWAuthenticateParser(value).parse

  private class WWWAuthenticateParser(input: ParserInput)
      extends ChallengeParser[`WWW-Authenticate`](input) {
    def entry: Rule1[`WWW-Authenticate`] =
      rule {
        oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { (xs: Seq[Challenge]) =>
          `WWW-Authenticate`(xs.head, xs.tail: _*)
        }
      }
  }
}
