package org.http4s

import java.lang.{Long => JLong}
import org.http4s.util.Writer

package object headers {
  private val XB3Id64BitCharLength: Int = 16
  private val XB3Id128BitCharLength: Int = XB3Id64BitCharLength * 2
  private val XB3IdFormatZeroPad: String = Vector.fill(XB3Id128BitCharLength)("0").mkString
  private val BitsToNibbleRatio: Int = 4

  private[headers] def xB3RenderValueImpl(
      writer: Writer,
      idMostSigBits: Long,
      idLeastSigBits: Option[Long] = None): writer.type =
    idLeastSigBits match {
      case Some(idLsb) =>
        if (idMostSigBits == 0L && idLsb == 0L) {
          writer.append(XB3IdFormatZeroPad.take(XB3Id128BitCharLength))
        } else if (idMostSigBits == 0l) {
          val leadingLsbHexZeroCount = JLong.numberOfLeadingZeros(idLsb) / BitsToNibbleRatio
          writer
            .append(XB3IdFormatZeroPad.take(XB3Id64BitCharLength))
            .append(XB3IdFormatZeroPad.take(leadingLsbHexZeroCount))
            .append(idLsb.toHexString)
        } else if (idLsb == 0L) {
          val leadingMsbHexZeroCount = JLong.numberOfLeadingZeros(idMostSigBits) / BitsToNibbleRatio
          writer
            .append(XB3IdFormatZeroPad.take(leadingMsbHexZeroCount))
            .append(idMostSigBits.toHexString)
            .append(XB3IdFormatZeroPad.take(XB3Id64BitCharLength))
        } else {
          val leadingMsbHexZeroCount = JLong.numberOfLeadingZeros(idMostSigBits) / BitsToNibbleRatio
          val leadingLsbHexZeroCount = JLong.numberOfLeadingZeros(idLsb) / BitsToNibbleRatio
          writer
            .append(XB3IdFormatZeroPad.take(leadingMsbHexZeroCount))
            .append(idMostSigBits.toHexString)
            .append(XB3IdFormatZeroPad.take(leadingLsbHexZeroCount))
            .append(idLsb.toHexString)
        }
      case None =>
        if (idMostSigBits == 0L) {
          writer.append(XB3IdFormatZeroPad.take(XB3Id64BitCharLength))
        } else {
          val leadingMsbHexZeroCount = JLong.numberOfLeadingZeros(idMostSigBits) / BitsToNibbleRatio
          writer
            .append(XB3IdFormatZeroPad.take(leadingMsbHexZeroCount))
            .append(idMostSigBits.toHexString)
        }
    }
}
