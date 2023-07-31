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

import cats.parse._
import org.typelevel.ci._

object Authorization {
  // https://datatracker.ietf.org/doc/html/rfc7235#section-4.2
  private[http4s] val parser: Parser[Authorization] = {
    import org.http4s.internal.parsing.AuthRules.credentials
    credentials.map(Authorization(_))
  }

  def parse(s: String): ParseResult[Authorization] =
    ParseResult.fromParser(parser, "Invalid Authorization header")(s)

  def apply(basic: BasicCredentials): Authorization =
    Authorization(Credentials.Token(AuthScheme.Basic, basic.token))

  val name: CIString = ci"Authorization"

  implicit val headerInstance: Header[Authorization, Header.Single] =
    Header.createRendered(
      name,
      _.credentials,
      parse,
    )
}

final case class Authorization(credentials: Credentials)
