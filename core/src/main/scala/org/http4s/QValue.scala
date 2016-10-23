package org.http4s

import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context
import scalaz._

import macrocompat.bundle
import org.http4s.util.{Renderable, Writer}

/**
 * A Quality Value.  Represented as thousandths for an exact representation rounded to three
 * decimal places.
 *
 * @param thousandths between 0 (for q=0) and 1000 (for q=1)
 * @see [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.9 RFC 2616, Section 3.9]]
 */
final class QValue private[QValue] (val thousandths: Int) extends AnyVal with Ordered[QValue] with Renderable {
  def toDouble: Double = 0.001 * thousandths

  def isAcceptable: Boolean = thousandths > 0

  override def compare(that: QValue): Int = thousandths - that.thousandths

  def render(writer: Writer): writer.type = {
    if (thousandths == 1000) writer
    else {
      writer.append(";q=")
      formatq(writer)
    }
  }

  // Assumes that q is in the proper bounds, otherwise you get an exception!
  private def formatq(b: Writer): b.type = {
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
        if (mod100 == 0) return b  // First digit only
      } else b.append('0')

      val mod10 = thousandths % 10

      if (thousandths > 9) {
        b.append(convert(mod100 / 10))
        if (mod10 == 0) return b  // Second digit only
      } else b.append('0')

      b.append(convert(mod10))   // Last digit
    }
  }
}

object QValue extends QValueInstances with QValueFunctions {
  lazy val One: QValue = new QValue(1000) // scalastyle:ignore
  lazy val Zero: QValue = new QValue(0)

  private def mkQValue(thousandths: Int, s: => String): ParseResult[QValue] = {
    if (thousandths < 0 || thousandths > 1000) ParseResult.fail("Invalid q-value", s"$s must be between 0.0 and 1.0")
    else ParseResult.success(new QValue(thousandths))
  }

  def fromThousandths(thousandths: Int): ParseResult[QValue] =
    mkQValue(thousandths, (thousandths * .001).toString)

  def fromDouble(d: Double): ParseResult[QValue] =
    mkQValue(Math.round(1000 * d).toInt, d.toString)

  def fromString(s: String): ParseResult[QValue] =
    try fromDouble(s.toDouble)
    catch { case e: NumberFormatException => ParseResult.fail("Invalid q-value", s"${s} is not a number") }

  /** Exists to support compile-time verified literals. Do not call directly. */
  def ☠(thousandths: Int): QValue = new QValue(thousandths)

  @bundle
  class Macros(val c: Context) {
    import c.universe._

    def qValueLiteral(d: c.Expr[Double]): Tree = {
      d.tree match {
        case Literal(Constant(d: Double)) =>
          QValue.fromDouble(d).fold(
            e => c.abort(c.enclosingPosition, e.details),
            qValue => q"_root_.org.http4s.QValue.☠(${qValue.thousandths})"
          )
        case _ =>
          c.abort(c.enclosingPosition, s"literal Double value required")
      }
    }
  }
}

trait QValueInstances {
  implicit val qValueOrder = Order.fromScalaOrdering[QValue]
  implicit val qValueShow = Show.showA[QValue]
}

trait QValueFunctions {
  /**
   * Supports a literal syntax for validated QValues.
   *
   * Example:
   * {{{
   * q(0.5).success == QValue.fromDouble(0.5)
   * q(1.1) // does not compile: out of range
   * val d = 0.5
   * q(d) // does not compile: not a literal
   * }}}
   */
  def q(d: Double): QValue = macro QValue.Macros.qValueLiteral
}

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}
