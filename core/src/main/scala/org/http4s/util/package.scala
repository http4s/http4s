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

import cats.ApplicativeThrow
import fs2._
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets

package object util {

  /** Converts ASCII encoded byte stream to a stream of `String`. */
  private[http4s] def asciiDecode[F[_]](implicit F: ApplicativeThrow[F]): Pipe[F, Byte, String] =
    _.chunks.through(asciiDecodeC)

  private def asciiCheck(b: Byte) = 0x80 & b

  /** Converts ASCII encoded `Chunk[Byte]` inputs to `String`. */
  private[http4s] def asciiDecodeC[F[_]](implicit
      F: ApplicativeThrow[F]
  ): Pipe[F, Chunk[Byte], String] = { in =>
    def tailRecAsciiCheck(i: Int, bytes: Array[Byte]): Stream[F, String] =
      if (i == bytes.length)
        Stream.emit(new String(bytes, StandardCharsets.US_ASCII))
      else if (asciiCheck(bytes(i)) == 0x80)
        Stream.raiseError[F](
          new IllegalArgumentException("byte stream is not encodable as ascii bytes")
        )
      else
        tailRecAsciiCheck(i + 1, bytes)

    in.flatMap(c => tailRecAsciiCheck(0, c.toArray))
  }

  @deprecated("use org.typelevel.ci.CIString", "0.22")
  type CaseInsensitiveString = CIString

  @deprecated("use org.typelevel.ci.CIString", "0.22")
  val CaseInsensitiveString = CIString
}
