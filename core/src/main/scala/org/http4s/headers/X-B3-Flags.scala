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
package headers

import cats.parse.Numbers
import cats.parse.Parser0
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

object `X-B3-Flags` {

  def parse(s: String): ParseResult[`X-B3-Flags`] =
    ParseResult.fromParser(parser, "Invalid X-B3-Flags header")(s)

  private[http4s] val parser: Parser0[`X-B3-Flags`] =
    Numbers.digits
      .mapFilter { str =>
        try Some(str.toLong)
        catch { case _: NumberFormatException => None }
      }
      .map(fromLong)

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

  implicit val headerInstance: Header[`X-B3-Flags`, Header.Single] =
    Header.createRendered(
      ci"X-B3-Flags",
      h =>
        new Renderable {
          def render(writer: Writer): writer.type =
            writer.append(h.flags.foldLeft(0L)((sum, next) => sum + next.longValue).toString)

        },
      parse,
    )

}

final case class `X-B3-Flags`(flags: Set[`X-B3-Flags`.Flag])
