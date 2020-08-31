/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `X-B3-Flags` extends HeaderKey.Internal[`X-B3-Flags`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`X-B3-Flags`] =
    HttpHeaderParser.X_B3_FLAGS(s)

  sealed trait Flag extends Product with Serializable {
    def longValue: Long
  }

  object Flag {
    case object Debug extends Flag {
      override def longValue: Long = 1 << 0
    }
    case object SamplingSet extends Flag {
      override def longValue: Long = 1 << 1
    }
    case object Sampled extends Flag {
      override def longValue: Long = 1 << 2
    }
  }

  private def bitIsSet(bit: Long, flagBits: Long): Boolean =
    (flagBits & bit) == bit

  // Pure API, despite internal mutation.
  def fromLong(flagBits: Long): `X-B3-Flags` = {
    var flags: Set[`X-B3-Flags`.Flag] = Set.empty

    if (bitIsSet(Flag.Debug.longValue, flagBits))
      flags = flags + Flag.Debug

    if (bitIsSet(Flag.SamplingSet.longValue, flagBits))
      flags = flags + Flag.SamplingSet

    if (bitIsSet(Flag.Sampled.longValue, flagBits))
      flags = flags + Flag.Sampled

    `X-B3-Flags`(flags)
  }
}

final case class `X-B3-Flags`(flags: Set[`X-B3-Flags`.Flag]) extends Header.Parsed {
  override def key: `X-B3-Flags`.type = `X-B3-Flags`

  override def renderValue(writer: Writer): writer.type =
    writer.append(flags.foldLeft(0L)((sum, next) => sum + next.longValue).toString)
}
