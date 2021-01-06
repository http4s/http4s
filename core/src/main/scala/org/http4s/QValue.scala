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

import cats.parse.Parser0
import cats.{Order, Show}
import org.http4s.util.Writer

import scala.reflect.macros.whitebox

/** A Quality Value.  Represented as thousandths for an exact representation rounded to three
  * decimal places.
  *
  * @param thousandths between 0 (for q=0) and 1000 (for q=1)
  * @see [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.9 RFC 2616, Section 3.9]]
  */
final class QValue private (val thousandths: Int) extends AnyVal with Ordered[QValue] {
  def toDouble: Double = 0.001 * thousandths

  def isAcceptable: Boolean = thousandths > 0

  override def toString = s"QValue(${0.001 * thousandths})"

  override def compare(that: QValue): Int = thousandths - that.thousandths

  def render(writer: Writer): writer.type =
    if (thousandths == 1000) writer
    else {
      writer.append(";q=")
      formatq(writer)
    }

  // Assumes that q is in the proper bounds, otherwise you get an exception!
  private def formatq(b: Writer): b.type =
    // Skip the rest of the stuff if we are 1.0
    if (thousandths == 1000) b.append("1.0")
    else if (thousandths == 0) b.append('0')
    else {
      // Need to start appending stuff
      b.append("0.")

      @inline
      def convert(i: Int): Char = ('0' + i).toChar

      val mod100 = thousandths % 100

      if (thousandths > 99) {
        b.append(convert(thousandths / 100))
        if (mod100 == 0) return b // First digit only
      } else b.append('0')

      val mod10 = thousandths % 10

      if (thousandths > 9) {
        b.append(convert(mod100 / 10))
        if (mod10 == 0) return b // Second digit only
      } else b.append('0')

      b.append(convert(mod10)) // Last digit
    }
}

object QValue {
  lazy val One: QValue = new QValue(1000)
  lazy val Zero: QValue = new QValue(0)

  private def mkQValue(thousandths: Int, s: => String): ParseResult[QValue] =
    if (thousandths < 0 || thousandths > 1000)
      ParseResult.fail("Invalid q-value", s"$s must be between 0.0 and 1.0")
    else ParseResult.success(new QValue(thousandths))

  def fromThousandths(thousandths: Int): ParseResult[QValue] =
    mkQValue(thousandths, (thousandths * .001).toString)

  def fromDouble(d: Double): ParseResult[QValue] =
    mkQValue(Math.round(1000 * d).toInt, d.toString)

  def fromString(s: String): ParseResult[QValue] =
    try fromDouble(s.toDouble)
    catch {
      case _: NumberFormatException => ParseResult.fail("Invalid q-value", s"$s is not a number")
    }

  def unsafeFromString(s: String): QValue =
    fromString(s).fold(throw _, identity)

  private[http4s] val parser: Parser0[QValue] = {
    import cats.parse.Parser.{char => ch, _}
    import cats.parse.Rfc5234._
    import org.http4s.parser.Rfc2616BasicRules.optWs

    val qValue = string(ch('0') *> (ch('.') *> digit.rep).rep0)
      .mapFilter(
        QValue
          .fromString(_)
          .toOption
      )
      .orElse(
        ch('1') *> (ch('.') *> ch('0').rep0.void).?.as(One)
      )

    ((ch(';') *> optWs *> ignoreCaseChar('q') *> ch('=')) *> qValue).backtrack
      .orElse(pure(QValue.One))
  }

  def parse(s: String): ParseResult[QValue] =
    ParseResult.fromParser(parser, "Invalid Q-Value")(s)

  /** Exists to support compile-time verified literals. Do not call directly. */
  def ☠(thousandths: Int): QValue = new QValue(thousandths)

  class Macros(val c: whitebox.Context) {
    import c.universe._

    def qValueLiteral(d: c.Expr[Double]): Tree =
      d.tree match {
        case Literal(Constant(d: Double)) =>
          QValue
            .fromDouble(d)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              qValue => q"_root_.org.http4s.QValue.☠(${qValue.thousandths})"
            )
        case _ =>
          c.abort(c.enclosingPosition, s"literal Double value required")
      }
  }

  /** Supports a literal syntax for validated QValues.
    *
    * Example:
    * {{{
    * q(0.5).success == QValue.fromDouble(0.5)
    * q(1.1) // does not compile: out of range
    * val d = 0.5
    * q(d) // does not compile: not a literal
    * }}}
    */
  @deprecated("""use qValue"" string interpolation instead""", "0.20")
  def q(d: Double): QValue = macro Macros.qValueLiteral

  implicit val http4sOrderForQValue: Order[QValue] = Order.fromOrdering[QValue]
  implicit val http4sShowForQValue: Show[QValue] = Show.fromToString[QValue]
  implicit val http4sHttpCodecForQValue: HttpCodec[QValue] = new HttpCodec[QValue] {
    def parse(s: String): ParseResult[QValue] = QValue.parse(s)
    def render(writer: Writer, q: QValue): writer.type = q.render(writer)
  }
}

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}
