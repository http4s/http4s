package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer
import org.http4s.{ Header, HeaderKey, ParseResult }

import scalaz.ISet

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

  def fromLong(flagBits: Long): `X-B3-Flags` = {
    def getFlags(x: Long): Set[`X-B3-Flags`.Flag] = {

      def bitIsSet(theBit: Long): Boolean =
        (x & theBit) == theBit

      def addFlagIfFound(flag: Flag): Set[Flag] => Set[Flag] = { flags =>
        if (bitIsSet(flag.longValue)) flags + flag
        else flags
      }

      val addFlagsIfFound: Set[Flag] => Set[Flag] =
        addFlagIfFound(Flag.Debug)
          .andThen(addFlagIfFound(Flag.Sampled))
          .andThen(addFlagIfFound(Flag.SamplingSet))

      addFlagsIfFound(Set.empty)
    }

    `X-B3-Flags`(getFlags(flagBits))
  }
}

final case class `X-B3-Flags`(flags: Set[`X-B3-Flags`.Flag]) extends Header.Parsed {
  override def key: `X-B3-Flags`.type = `X-B3-Flags`

  override def renderValue(writer: Writer): writer.type = {
    writer.append(flags.foldLeft(0L)((sum, next) => sum + next.longValue).toString)
  }
}
