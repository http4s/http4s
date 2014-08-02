package org.http4s

import org.http4s.util.{Renderable, StringWriter, Writer, ValueRenderable}

import scalaz.Order

final class QValue private (val thousandths: Int) extends AnyVal with Ordered[QValue] with Renderable {

  def toDouble: Double = 0.001 * thousandths

  def isAcceptable: Boolean = thousandths > 0

  override def compare(that: QValue): Int = that.thousandths - thousandths

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

  val One: QValue = fromThousandths(1000)
  val Zero: QValue = fromThousandths(0)

  def fromThousandths(i: Int): QValue = { 
    checkBounds(i) 
    new QValue(i) 
  }

  //the 0.0005 is to round up else  0.7f -> 699
  def fromDouble(d: Double): QValue = fromThousandths((QValue.MaxThousandths * d + 0.0005).toInt)
  
  def fromString(s: String): QValue = fromDouble(java.lang.Double.parseDouble(s))

  private[http4s] def checkBounds(q: Int): Unit = {
    if (q > MaxThousandths || q < 0)
      throw new IllegalArgumentException(s"Invalid qValue. 0.0 <= q <= 1.0, specified: $q")
  }

  implicit def toDouble(q: QValue): Double = q.toDouble

  implicit def doubleToQ(d: Double): QValue = QValue.fromDouble(d)
}

trait HasQValue {
  def qValue: QValue
  def withQValue(q: QValue): HasQValue
}

