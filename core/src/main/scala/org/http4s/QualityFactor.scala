package org.http4s

import org.http4s.util.{StringWriter, Writer, ValueRenderable}

trait QualityFactor {

  def q: Q

  def withQuality(q: Q): QualityFactor
}

final case class Q private(intValue: Int) extends Ordering[Q] with ValueRenderable {

  def doubleValue: Double = 0.001*(intValue.toDouble)

  def stringValue: String = value

  def unacceptable: Boolean = intValue == Q.MIN_VALUE

  def headerString: String =  {
    if (intValue != Q.MAX_VALUE) formatq((new StringWriter).append("; q=")).result()
    else ""
  }

  def compare(x: Q, y: Q): Int = Q.compare(x, y)

  def renderValue[W <: Writer](writer: W): writer.type = {
    if (intValue == Q.MAX_VALUE) writer
    else {
      writer.append(";q=")
      formatq(writer)
    }
  }

  // Assumes that q is in the proper bounds, otherwise you get an exception!
  private def formatq[W <: Writer](b: W): W = {

    // Skip the rest of the stuff if we are 1.0
    if (intValue == 1000) return b.append("1.0")
    if (intValue == 0) return b.append('0')

    // Need to start appending stuff
    b.append("0.")


    @inline
    def convert(i: Int): Char = ('0' + i).toChar

    val mod100 = intValue % 100

    if (intValue > 99) {
      b.append(convert(intValue / 100))
      if (mod100 == 0) return b  // First digit only
    } else b.append('0')

    val mod10 = intValue % 10

    if (intValue > 9) {
      b.append(convert(mod100 / 10))
      if (mod10 == 0) return b  // Second digit only
    } else b.append('0')

    b.append(convert(mod10))   // Last digit
  }
}

object Q {

  val Unity: Q = fromInt(MAX_VALUE)

  def fromInt(i: Int): Q = { checkBounds((i)); Q(i) }

  //the 0.0005 is to round up else  0.7f -> 699
  def fromDouble(d: Double): Q = fromInt((Q.MAX_VALUE*d + 0.0005).toInt)
  
  def fromString(s: String): Q = fromDouble(java.lang.Double.parseDouble(s))

  @inline
  def MAX_VALUE = 1000

  @inline
  def MIN_VALUE = 0

  private[http4s] def checkBounds(q: Int): Unit = {
    if (q > MAX_VALUE || q < 0)
      throw new IllegalArgumentException(s"Invalid quality. 0.0 <= q <= 1.0, specified: $q")
  }

  private def compare(x: Q, y: Q): Int = {
    x.intValue - y.intValue
  }
  
  implicit def toDouble(q: Q): Double = q.doubleValue

  implicit def doubleToQ(d: Double): Q = Q.fromDouble(d)

  // Charset are sorted by the quality value, from greatest to least
  implicit def qfactorOrdering = new Ordering[Q] {
    def compare(x: Q, y: Q): Int = Q.compare(x, y)
  }
}