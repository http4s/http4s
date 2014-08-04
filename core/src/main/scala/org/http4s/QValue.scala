package org.http4s

import org.http4s.util.{Renderable, StringWriter, Writer, ValueRenderable}

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.util.control.NoStackTrace
import scalaz.{Show, Equal, Validation, Order}
import scalaz.syntax.validation._

/**
 * A Quality Value.  Represented as thousandths for an exact representation rounded to three
 * decimal places.
 *
 * @param thousandths betweeen 0 (for q=0) and 1000 (for q=1)
 */
final class QValue private (val thousandths: Int) extends AnyVal with Ordered[QValue] with Renderable {
  def toDouble: Double = 0.001 * thousandths

  def isAcceptable: Boolean = thousandths > 0

  override def compare(that: QValue): Int = thousandths - that.thousandths

  def render[W <: Writer](writer: W): writer.type = {
    if (thousandths == 1000) writer
    else {
      writer.append(";q=")
      formatq(writer)
    }
  }

  // Assumes that q is in the proper bounds, otherwise you get an exception!
  private def formatq[W <: Writer](b: W): b.type = {
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
  val One: QValue = new QValue(1000)
  val Zero: QValue = new QValue(0)

  private def mkQValue(thousandths: Int, s: => String): Validation[InvalidQValue, QValue] = {
    if (thousandths < 0 || thousandths > 1000) InvalidQValue(s).fail
    else new QValue(thousandths).success
  }

  def fromThousandths(thousandths: Int): Validation[InvalidQValue, QValue] =
    mkQValue(thousandths, (thousandths * .001).toString)

  def fromDouble(d: Double): Validation[InvalidQValue, QValue] =
    mkQValue(Math.round(1000 * d).toInt, d.toString)
  
  def fromString(s: String): Validation[InvalidQValue, QValue] =
    try fromDouble(s.toDouble)
    catch { case e: NumberFormatException => InvalidQValue(s).fail }

  object macros {
    def qValueLiteral(c: Context)(d: c.Expr[Double]): c.Expr[QValue] = {
      import c.universe._

      d.tree match {
        // TODO Seems like I should be able to quasiquote here.
        case Literal(Constant(d: Double)) =>
          QValue.fromDouble(d).fold(
            e => c.abort(c.enclosingPosition, e.getMessage),
            // TODO I think we could just use qValue if we had a Liftable[QValue], but I can't
            // figure it out for Scala 2.10.
            qValue => c.Expr(q"new QValue(${qValue.thousandths})")
          )
        case _ =>
          c.abort(c.enclosingPosition, s"q syntax only works for literal doubles: ${showRaw(d.tree)}")
      }
    }
  }
}

trait QValueInstances {
  implicit val QValueEqual = Equal.equalA[QValue]
  implicit val QValueShow = Show.showA[QValue]
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
  def q(d: Double): QValue = macro QValue.macros.qValueLiteral
}

case class InvalidQValue(string: String) extends Http4sException(s"Invalid QValue: ${string}") with NoStackTrace

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}
