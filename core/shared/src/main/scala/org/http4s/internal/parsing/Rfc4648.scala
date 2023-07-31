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
package internal.parsing

import cats.parse.Parser
import cats.parse.Parser.char
import cats.parse.Parser.charIn
import cats.parse.Rfc5234.alpha
import cats.parse.Rfc5234.digit

/** Common rules defined in Rfc4648
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc4648]]
  */

private[http4s] object Rfc4648 {
  object Base64 {
    /* https://datatracker.ietf.org/doc/html/rfc4648#page-5 */
    val token: Parser[String] =
      (charIn("+/").orElse(digit).orElse(alpha).rep ~ char('=').rep0(0, 2)).string
  }
}
