package org.http4s

import org.http4s.util.{Renderable, StringWriter, Writer, ValueRenderable}

import scala.util.control.NoStackTrace
import scalaz.{Validation, Order}
import scalaz.syntax.validation._

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

  //the 0.0005 is to round up else  0.7f -> 699
  def fromDouble(d: Double): Validation[InvalidQValue, QValue] =
    mkQValue((QValue.MaxThousandths * d + 0.0005).toInt, d.toString)
  
  def fromString(s: String): Validation[InvalidQValue, QValue] =
    try fromDouble(s.toDouble)
    catch { case e: NumberFormatException => InvalidQValue(s).fail }

  private[http4s] def checkBounds(q: Int): Unit = {
    if (q > MaxThousandths || q < 0)
      throw new IllegalArgumentException(s"Invalid qValue. 0.0 <= q <= 1.0, specified: $q")
  }

  @deprecated("fromDouble and deal with validation.  Consider macro for literals?", "0.3")
  implicit def doubleToQ(d: Double): QValue = QValue.fromDouble(d).fold(throw _, identity)
}

case class InvalidQValue(s: String) extends Http4sException(s) with NoStackTrace

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}

