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

import cats.Hash
import cats.Order
import cats.Show
import cats.kernel.BoundedEnumerable
import cats.parse.Parser0
import org.http4s.util.Writer

/** A Quality Value.  Represented as thousandths for an exact representation rounded to three
  * decimal places.
  *
  * @param thousandths between 0 (for q=0) and 1000 (for q=1)
  * @see [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.9 RFC 2616, Section 3.9, Quality Values]]
  */
final class QValue private (val thousandths: Int) extends AnyVal with Ordered[QValue] {
  def toDouble: Double = 0.001 * thousandths

  def isAcceptable: Boolean = thousandths > 0

  override def toString: String = s"QValue(${0.001 * thousandths})"

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

object QValue extends QValuePlatform {
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

  private[http4s] val parser: Parser0[QValue] = {
    import cats.parse.Parser.{char => ch, _}
    import cats.parse.Rfc5234._
    import org.http4s.internal.parsing.CommonRules.ows

    val qValue = string(ch('0') *> (ch('.') *> digit.rep).rep0)
      .mapFilter(
        QValue
          .fromString(_)
          .toOption
      )
      .orElse(
        ch('1') *> (ch('.') *> ch('0').rep0.void).?.as(One)
      )

    ((ch(';') *> ows *> ignoreCaseChar('q') *> ch('=')) *> qValue).backtrack
      .orElse(pure(QValue.One))
  }

  def parse(s: String): ParseResult[QValue] =
    ParseResult.fromParser(parser, "Invalid Q-Value")(s)

  /** Exists to support compile-time verified literals. Do not call directly. */
  @deprecated(
    """QValue literal is deprecated.  Import `org.http4s.implicits._` and use the qValue"" string context""",
    "0.22.2",
  )
  def ☠(thousandths: Int): QValue = new QValue(thousandths)

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

  implicit val stdLibOrderingInstance: Ordering[QValue] =
    catsInstancesForHttp4sQValue.toOrdering
}

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}
