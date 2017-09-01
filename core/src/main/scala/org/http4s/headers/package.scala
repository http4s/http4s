package org.http4s

import org.http4s.util.Writer
import java.lang.{Long => JLong}

package object headers {

  private[headers] def xB3RenderValueImpl(writer: Writer, id: Long): writer.type =
    if (id == 0L) {
      val idLength = 16
      writer.append("0" * idLength)
    } else {
      val bitsToHalfByteRatio = 4
      val leadingHexZeroCount =
        JLong.numberOfLeadingZeros(id) / bitsToHalfByteRatio
      writer
        .append("0" * leadingHexZeroCount)
        .append(id.toHexString)
    }
}
