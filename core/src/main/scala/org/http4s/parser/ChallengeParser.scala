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

package org.http4s.parser

import org.http4s.{Challenge, Header}
import org.http4s.internal.parboiled2._

abstract private[parser] class ChallengeParser[H <: Header](input: ParserInput)
    extends Http4sHeaderParser[H](input) {
  def ChallengeRule: Rule1[Challenge] =
    rule {
      Token ~ oneOrMore(LWS) ~ zeroOrMore(AuthParam).separatedBy(ListSep) ~> {
        (scheme: String, params: collection.Seq[(String, String)]) =>
          val (realms, otherParams) = params.partition(_._1 == "realm")
          Challenge(scheme, realms.headOption.map(_._2).getOrElse(""), otherParams.toMap)
      }
    }

  def AuthParam: Rule1[(String, String)] =
    rule {
      Token ~ "=" ~ (Token | QuotedString) ~> { (a: String, b: String) =>
        (a, b)
      }
    }
}
