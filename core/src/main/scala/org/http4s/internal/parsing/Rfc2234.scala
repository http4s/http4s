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

package org.http4s.internal.parsing

import cats.parse.Parser
import cats.parse.Parser.charIn

/** Common rules defined in RFC2234.
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc2234] RFC2234, Augmented BNF for Syntax Specifications: ABNF]
  */
private[http4s] object Rfc2234 {
  /* ALPHA          =  %x41-5A / %x61-7A   ; A-Z / a-z */
  val alpha: Parser[Char] =
    charIn(0x41.toChar to 0x5a.toChar).orElse(charIn(0x61.toChar to 0x7a.toChar))

  /* DIGIT          =  %x30-39
   *                       ; 0-9 */
  val digit: Parser[Char] =
    charIn(0x30.toChar to 0x39.toChar)
}
