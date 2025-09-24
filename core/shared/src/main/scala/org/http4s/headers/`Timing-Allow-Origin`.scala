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

package org.http4s.headers

import cats.Semigroup
import cats.data.NonEmptyList
import cats.syntax.all.*
import org.http4s.Header
import org.http4s.ParseResult
import org.http4s.internal.parsing.CommonRules
import org.typelevel.ci.*


sealed trait `Timing-Allow-Origin`
case object TimingAllowAllOrigins extends `Timing-Allow-Origin`
final case class TimingAllowSelectedOrigins(head: CIString, tail: CIString*) extends `Timing-Allow-Origin`



object `Timing-Allow-Origin` {
  def apply(headers: Option[NonEmptyList[CIString]]): `Timing-Allow-Origin` = headers match {
    case Some(headers)  => TimingAllowSelectedOrigins(headers.head, headers.tail)
    case _              => TimingAllowAllOrigins
  }


  private def fromString(s: String) = if (s == "*")
                                Option.empty
                              else Option(CIString(s))

  private def fromNel(nel: NonEmptyList[Option[CIString]]) =
    if (nel.toList.contains(Option.empty)) {
      if (nel.size == 1)
        ParseResult.success(TimingAllowAllOrigins)
      else
        ParseResult.fail(
          "Invalid origin value",
          s"Timing-Allow-Origin must contain either a '*' or a non-empty list of allowed origins"
        )
    } else ParseResult.success(
      apply(nel.sequence)
    )

  private[http4s] def parse(s: String): ParseResult[`Timing-Allow-Origin`] =
    ParseResult.fromParser(parser, "Invalid Timing-Allow-Origin headers")(s).flatten

  private[http4s] val parser =
    CommonRules
      .headerRep1(CommonRules.token.map(fromString))
      .map(fromNel)

  implicit val headerInstance: Header[`Timing-Allow-Origin`, Header.Recurring] =
    Header.createRendered(
      ci"Timing-Allow-Origin",
      {
        case TimingAllowAllOrigins => List.empty
        case TimingAllowSelectedOrigins(origin, origins*) => origin :: origins.toList
      },
      parse,
    )

  implicit val timingAllowOriginSemigroup: Semigroup[`Timing-Allow-Origin`] =
    (a, b) => (a, b) match {
      case (TimingAllowAllOrigins, _) | (_, TimingAllowAllOrigins) => TimingAllowAllOrigins
      case (TimingAllowSelectedOrigins(origin1, origins1*), TimingAllowSelectedOrigins(origin2, origins2*)) =>
        TimingAllowSelectedOrigins(origin1, (origin2 :: origins1.toList) ::: origins2.toList)
    }
}