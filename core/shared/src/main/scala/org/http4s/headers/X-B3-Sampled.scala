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

import cats.parse.Parser
import cats.parse.Rfc5234
import org.typelevel.ci._

object `X-B3-Sampled` {

  def parse(s: String): ParseResult[`X-B3-Sampled`] =
    ParseResult.fromParser(parser, "Invalid X-B3-Sampled header")(s)

  private[http4s] val parser: Parser[`X-B3-Sampled`] =
    Rfc5234.bit.map(s => `X-B3-Sampled`(s == '1'))

  implicit val headerInstance: Header[`X-B3-Sampled`, Header.Single] =
    Header.create(
      ci"X-B3-Sampled",
      v => if (v.sampled) "1" else "0",
      parse,
    )

}

final case class `X-B3-Sampled`(sampled: Boolean)
