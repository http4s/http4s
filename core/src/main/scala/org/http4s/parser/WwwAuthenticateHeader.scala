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
package org.http4s.parser

import org.parboiled2.{Rule1, ParserInput}
import org.http4s.Header.`WWW-Authenticate`
import org.http4s.Challenge

/**
 * @author Bryce Anderson
 *         Created on 1/29/14
 */
private[parser] trait WwwAuthenticateHeader {

  def WWW_AUTHENTICATE(value: String) = new AuthenticateParser(value).parse

  private class AuthenticateParser(input: ParserInput) extends Http4sHeaderParser[`WWW-Authenticate`](input) {
    def entry: Rule1[`WWW-Authenticate`] = rule {
        oneOrMore(ChallengeRule).separatedBy(ListSep) ~ EOI ~> { xs: Seq[Challenge] =>
          `WWW-Authenticate`(xs.head, xs.tail: _*)
        }
    }

    def ChallengeRule: Rule1[Challenge] = rule {
      Token ~ oneOrMore(LWS) ~ zeroOrMore(AuthParam).separatedBy(ListSep) ~> {
        (scheme: String, params: Seq[(String, String)]) =>
          val (realms, otherParams) = params.partition(_._1 == "realm")
          Challenge(scheme, realms.headOption.map(_._2).getOrElse(""), otherParams.toMap)
      }
    }

    def AuthParam: Rule1[(String, String)] = rule {
      Token ~ "=" ~ (Token | QuotedString) ~> {(a: String, b: String) => (a,b) }
    }
  }

}
