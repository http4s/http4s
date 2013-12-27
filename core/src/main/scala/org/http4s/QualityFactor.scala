package org.http4s

/**
 * @author Bryce Anderson
 *         Created on 12/26/13
 */

trait QualityFactor {

  def q: Float

  def withQuality(q: Float): QualityFactor

  // Assumes that q is in the proper bounds, otherwise you get an exception!
  private def formatq(): String = {
    assert(q >= 0.0f && q < 1.0f)

    val b = new StringBuilder(9,"; q=0.")
    val d3 = (1000*q).toInt % 10  // Thousandths place
    val d2 = (100*q).toInt  % 10  // Hundredths place
    val d1 = (10*q).toInt         // Tenths place

    @inline
    def convert(i: Int): Char = ('0' + i).toChar

    b.append(convert(d1))

    if (d3 != 0) b.append(convert(d2)).append(convert(d3))
    else if (d2 != 0) b.append(convert(d2))

    b.result()
  }

  protected final def qstring: String =  if (q != 1.0f) formatq else ""

  protected def checkQuality(q: Float): Unit = {
    if (q > 1.0f || q < 0.0f)
      throw new IllegalArgumentException(s"Invalid quality. 0.0 <= q <= 1.0, specified: $q")
  }
}

object QualityFactor {
  // Charset are sorted by the quality value, from greatest to least
  implicit def qfactorOrdering = new Ordering[QualityFactor] {
    def compare(x: QualityFactor, y: QualityFactor): Int = {
      val diff = y.q - x.q
      (diff*1000).toInt       // Will ignore significance below the third decimal place
    }
  }
}