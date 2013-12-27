package org.http4s



/**
 * @author Bryce Anderson
 *         Created on 12/26/13
 */

trait QHelper {
  // Assumes that q is in the proper bounds, otherwise you get an exception!
  def formatq(q: Float): String = {
    assert(q >= 0.0f && q < 1.0f)

    val b = new StringBuilder(9,"; q=0.")
    val d3 = (1000*q).toInt % 10  // Thousandths place
    val d2 = (100*q).toInt  % 10  // Hundredths place
    val d1 = (10*q).toInt         // Tenths place

    b.append(d1)

    if (d3 != 0) b.append(d2).append(d3)
    else if (d2 != 0) b.append(d2)

    b.result()
  }

  def checkQuality(q: Float): Unit = {
    if (q > 1.0f || q < 0.0f)
      throw new IllegalArgumentException(s"Invalid quality. 0.0 <= q <= 1.0, specified: $q")
  }
}