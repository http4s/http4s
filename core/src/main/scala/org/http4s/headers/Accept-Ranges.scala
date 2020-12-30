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
import cats.parse.{Parser => P}
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Writer

object `Accept-Ranges` extends HeaderKey.Internal[`Accept-Ranges`] with HeaderKey.Singleton {
  def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply((first +: more).toList)
  def bytes: `Accept-Ranges` = apply(RangeUnit.Bytes)
  def none: `Accept-Ranges` = apply(Nil)

  override def parse(s: String): ParseResult[`Accept-Ranges`] =
    ParseResult.fromParser(parser, "Accept-Ranges header")(s)

  /* https://tools.ietf.org/html/rfc7233#appendix-C */
  val parser: P[`Accept-Ranges`] = {

    val none = P.string1("none").as(Nil)

    val rangeUnit = Rfc7230.token.map(org.http4s.RangeUnit.apply)

    /*
     Accept-Ranges     = acceptable-ranges
     OWS               = <OWS, see [RFC7230], Section 3.2.3>
     acceptable-ranges = ( *( "," OWS ) range-unit *( OWS "," [ OWS range-unit ] ) ) / "none"
     */
    val acceptableRanges: P[List[RangeUnit]] =
      P.oneOf(
        List(
          none,
          Rfc7230.headerRep1(rangeUnit).map(_.toList)
        )
      )

    acceptableRanges.map(headers.`Accept-Ranges`.apply)
  }
}

final case class `Accept-Ranges` private[http4s] (rangeUnits: List[RangeUnit])
    extends Header.Parsed {
  def key: `Accept-Ranges`.type = `Accept-Ranges`
  def renderValue(writer: Writer): writer.type =
    if (rangeUnits.isEmpty) writer.append("none")
    else {
      writer.append(rangeUnits.head)
      rangeUnits.tail.foreach(r => writer.append(", ").append(r))
      writer
    }
}
