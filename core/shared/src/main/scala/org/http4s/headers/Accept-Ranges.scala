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
import cats.parse.{Parser0 => P0}
import cats.syntax.all._
import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderer
import org.typelevel.ci._

object `Accept-Ranges` {
  def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply((first +: more).toList)
  def bytes: `Accept-Ranges` = apply(RangeUnit.Bytes)
  def none: `Accept-Ranges` = apply(Nil)

  def parse(s: String): ParseResult[`Accept-Ranges`] =
    ParseResult.fromParser(parser, "Invalid Accept-Ranges header")(s)

  /* https://datatracker.ietf.org/doc/html/rfc7233#appendix-C */
  val parser: P0[`Accept-Ranges`] = {

    val none = Parser.string("none").as(Nil)

    val rangeUnit = CommonRules.token.map(org.http4s.RangeUnit.apply)

    /*
     Accept-Ranges     = acceptable-ranges
     OWS               = <OWS, see [RFC7230], Section 3.2.3>
     acceptable-ranges = ( *( "," OWS ) range-unit *( OWS "," [ OWS range-unit ] ) ) / "none"
     */
    val acceptableRanges: P0[List[RangeUnit]] =
      Parser.oneOf0(
        List(
          none,
          CommonRules.headerRep1(rangeUnit).map(_.toList),
        )
      )

    acceptableRanges.map(headers.`Accept-Ranges`.apply)
  }

  implicit val headerInstance: Header[`Accept-Ranges`, Header.Single] =
    Header.create(
      ci"Accept-Ranges",
      _.rangeUnits.toNel match {
        case None => "none"
        case Some(nel) => Renderer.renderString(nel)
      },
      parse,
    )
}

final case class `Accept-Ranges`(rangeUnits: List[RangeUnit])
