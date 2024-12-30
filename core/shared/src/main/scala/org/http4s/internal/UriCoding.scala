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

package org.http4s.internal

import org.http4s.internal.CharPredicate.AlphaNum

import java.nio.CharBuffer
import java.nio.charset.{Charset => JCharset}

/* Exists to work around circular dependencies */
private[http4s] object UriCoding {
  val Unreserved: CharPredicate = AlphaNum ++ "-_.~"
  val QueryNoEncode: CharPredicate = Unreserved ++ "?/"

  def encode(
      toEncode: String,
      charset: JCharset,
      spaceIsPlus: Boolean,
      toSkip: Char => Boolean,
  ): String = {
    val in = charset.encode(toEncode)
    val out = CharBuffer.allocate((in.remaining() * 3).toInt)
    while (in.hasRemaining) {
      val c = in.get().toChar
      if (toSkip(c)) {
        out.put(c)
        ()
      } else if (c == ' ' && spaceIsPlus) {
        out.put('+')
        ()
      } else {
        out.put('%')
        out.put(HexUpperCaseChars((c >> 4) & 0xf))
        out.put(HexUpperCaseChars(c & 0xf))
        ()
      }
    }
    out.flip()
    out.toString
  }

  private val HexUpperCaseChars = (0 until 16).map { i =>
    Character.toUpperCase(Character.forDigit(i, 16))
  }
}
