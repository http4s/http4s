/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import org.http4s.util.Writer

import java.lang.{Long => JLong}

package object headers {
  private val XB3Id64BitCharLength: Int = 16
  private val XB3Id128BitCharLength: Int = XB3Id64BitCharLength * 2
  private val XB3IdFormatZeroPad: String = Vector.fill(XB3Id128BitCharLength)("0").mkString
  private val BitsToNibbleRatio: Int = 4

  private[headers] def xB3RenderValueImpl(
      writer: Writer,
      idMostSigBits: Long,
      idLeastSigBits: Option[Long] = None,
  ): writer.type =
    idLeastSigBits match {
      case Some(idLsb) =>
        if (idMostSigBits == 0L && idLsb == 0L)
          writer.append(XB3IdFormatZeroPad.take(XB3Id128BitCharLength))
        else if (idMostSigBits == 0L) {
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
        if (idMostSigBits == 0L)
          writer.append(XB3IdFormatZeroPad.take(XB3Id64BitCharLength))
        else {
          val leadingMsbHexZeroCount = JLong.numberOfLeadingZeros(idMostSigBits) / BitsToNibbleRatio
          writer
            .append(XB3IdFormatZeroPad.take(leadingMsbHexZeroCount))
            .append(idMostSigBits.toHexString)
        }
    }

  @deprecated("Deprecated in favor of ProductIdOrComment", "0.22.0-M1")
  type AgentToken = ProductIdOrComment

  @deprecated("Deprecated in favor of ProductComment", "0.22.0-M1")
  type AgentComment = ProductComment

  @deprecated("Deprecated in favor of ProductId", "0.22.0-M1")
  type AgentProduct = ProductId
}
