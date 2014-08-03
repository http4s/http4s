package org.http4s

import org.http4s.util.{Renderable, StringWriter, Writer, ValueRenderable}

import scala.language.experimental.macros
import scala.reflect.macros.Context
import scala.util.control.NoStackTrace
import scalaz.{Validation, Order}
import scalaz.syntax.validation._

final class QValue (val thousandths: Int) extends AnyVal with Ordered[QValue] with Renderable {

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

object QValue {
  private val MaxThousandths = 1000
  private val MinThousandths = 0

  val One: QValue = fromThousandths(1000).fold(throw _, identity)
  val Zero: QValue = fromThousandths(0).fold(throw _, identity)

  private def mkQValue(thousandths: Int, s: => String): Validation[InvalidQValue, QValue] = {
    if (thousandths < MinThousandths || thousandths > MaxThousandths) InvalidQValue(s).fail
    else new QValue(thousandths).success
  }

  def fromThousandths(thousandths: Int): Validation[InvalidQValue, QValue] =
    mkQValue(thousandths, (thousandths * .001).toString)

  def fromDouble(d: Double): Validation[InvalidQValue, QValue] =
    mkQValue(Math.round(QValue.MaxThousandths * d).toInt, d.toString)
  
  def fromString(s: String): Validation[InvalidQValue, QValue] =
    try fromDouble(s.toDouble)
    catch { case e: NumberFormatException => InvalidQValue(s).fail }

  object macros {
    def qValueLiteral(c: Context)(): c.Expr[QValue] = {
      import c.universe._

      val Apply(_, List(Apply(_, List(Literal(Constant(s: String)))))) = c.prefix.tree

      QValue.fromString(s).fold(
        e => c.abort(c.enclosingPosition, e.getMessage),
        // TODO I think we could just use qValue if we had a Liftable[QValue], but I can't
        // figure it out for Scala 2.10.
        qValue => c.Expr(q"QValue.fromThousandths(${qValue.thousandths}).fold(throw _, identity)")
      )
    }
  }
}

trait QValueSyntax {
  /**
   * Supports a literal syntax for valid QValues.
   *
   * Example:
   * {{{
   * qValue"0.5" == QValue.fromString("0.5").fold(throw _, identity)
   * qValue"1.1" // does not compile
   * }}}
   */
  implicit class QValueLiteral(sc: StringContext) {
    def qValue(): QValue = macro QValue.macros.qValueLiteral
  }
}

case class InvalidQValue(string: String) extends Http4sException(s"Invalid QValue: ${string}") with NoStackTrace

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}

