/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers
import cats.parse.{Parser => P}
import cats.implicits._
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Writer

object `Accept-Ranges` extends HeaderKey.Internal[`Accept-Ranges`] with HeaderKey.Singleton {
  def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply((first +: more).toList)
  def bytes: `Accept-Ranges` = apply(RangeUnit.Bytes)
  def none: `Accept-Ranges` = apply(Nil)

  override def parse(s: String): ParseResult[`Accept-Ranges`] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid accept-ranges", e.toString)
    }

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
