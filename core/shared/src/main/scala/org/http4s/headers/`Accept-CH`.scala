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

import cats.parse.Parser0
import org.http4s.internal.parsing.Rfc7230
import org.typelevel.ci._

/*
Accept-CH response header
see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-CH
 */
object `Accept-CH` {
  def parse(s: String): ParseResult[`Accept-CH`] =
    ParseResult.fromParser(parser, "Invalid Accept-CH header")(s)

  private[http4s] val parser: Parser0[`Accept-CH`] =
    Rfc7230.headerRep(Rfc7230.token.map(CIString(_))).map(`Accept-CH`(_))

  implicit val headerInstance: Header[`Accept-CH`, Header.Recurring] =
    Header.createRendered(
      ci"Accept-CH",
      _.clientHints,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Monoid[`Accept-CH`] =
    cats.Monoid.instance(
      `Accept-CH`(Nil),
      (one, two) => `Accept-CH`(one.clientHints ++ two.clientHints),
    )
}

final case class `Accept-CH`(clientHints: List[CIString])
