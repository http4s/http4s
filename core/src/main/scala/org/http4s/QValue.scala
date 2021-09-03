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

import cats.kernel.BoundedEnumerable
import cats.{Hash, Order, Show}
import org.http4s.internal.parboiled2.{Parser => PbParser}
import org.http4s.parser.{AdditionalRules, Http4sParser}
import org.http4s.util.Writer
import scala.reflect.macros.whitebox

/** A Quality Value. Represented as thousandths for an exact representation rounded to three decimal
  * places.
  *
  * @param thousandths
  *   between 0 (for q=0) and 1000 (for q=1)
  * @see
  *   [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.9 RFC 2616, Section 3.9, Quality Values]]
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

  def unapply(qValue: QValue): Option[Int] = Some(qValue.thousandths)

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

  def parse(s: String): ParseResult[QValue] =
    new Http4sParser[QValue](s, "Invalid q-value") with QValueParser {
      def main = QualityValue
    }.parse

  private[http4s] trait QValueParser extends AdditionalRules { self: PbParser =>
    def QualityValue =
      rule { // QValue is already taken
        ";" ~ OptWS ~ "q" ~ "=" ~ QValue | push(org.http4s.QValue.One)
      }
  }

  /** Exists to support compile-time verified literals. Do not call directly. */
  def ☠(thousandths: Int): QValue = new QValue(thousandths)

  @deprecated("This location of the implementation complicates Dotty support", "0.21.16")
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

  @deprecated("Use catsInstancesForHttp4sQValue", "0.21.20")
  val http4sOrderForQValue: Order[QValue] = Order.fromOrdering[QValue]
  @deprecated("Use catsInstancesForHttp4sQValue", "0.21.20")
  val http4sShowForQValue: Show[QValue] = Show.fromToString[QValue]
  @deprecated("Use catsInstancesForHttp4sQValue", "0.21.20")
  val http4sHttpCodecForQValue: HttpCodec[QValue] = new HttpCodec[QValue] {
    def parse(s: String): ParseResult[QValue] = QValue.parse(s)
    def render(writer: Writer, q: QValue): writer.type = q.render(writer)
  }

  implicit val catsInstancesForHttp4sQValue: Order[QValue]
    with Show[QValue]
    with Hash[QValue]
    with HttpCodec[QValue]
    with BoundedEnumerable[QValue] = new Order[QValue]
    with Show[QValue]
    with Hash[QValue]
    with HttpCodec[QValue]
    with BoundedEnumerable[QValue] { self =>
    // Order
    override def compare(x: QValue, y: QValue): Int = x.compare(y)

    // Show
    override def show(t: QValue): String = t.toString

    // Hash
    override def hash(x: QValue): Int = x.hashCode

    // HttpCodec
    override def parse(s: String): ParseResult[QValue] = QValue.parse(s)
    override def render(writer: Writer, q: QValue): writer.type = q.render(writer)

    // BoundedEnumerable
    override def partialNext(a: QValue): Option[QValue] = a match {
      case QValue.One => None
      case QValue(thousandths) if fromThousandths(thousandths).isLeft => None
      case QValue(thousandths) => Some(new QValue(thousandths + 1))
      case _ => None
    }
    override def partialPrevious(a: QValue): Option[QValue] = a match {
      case QValue.Zero => None
      case QValue(thousandths) if fromThousandths(thousandths).isLeft => None
      case QValue(thousandths) => Some(new QValue(thousandths - 1))
      case _ => None
    }
    override def order: Order[QValue] = self
    override def minBound: QValue = QValue.Zero
    override def maxBound: QValue = QValue.One
  }
}

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}
