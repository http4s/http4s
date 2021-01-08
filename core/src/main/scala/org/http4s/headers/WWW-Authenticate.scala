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

import cats.data.NonEmptyList
import cats.parse.Parser1
import org.http4s.internal.parsing.Rfc7235

object `WWW-Authenticate` extends HeaderKey.Internal[`WWW-Authenticate`] with HeaderKey.Recurring {
  private[http4s] val parser: Parser1[`WWW-Authenticate`] =
    Rfc7235.challenges.map(`WWW-Authenticate`.apply)

  override def parse(s: String): ParseResult[`WWW-Authenticate`] =
    ParseResult.fromParser(parser, "Invalid WWW-Authenticate")(s)
}

final case class `WWW-Authenticate`(values: NonEmptyList[Challenge])
    extends Header.RecurringRenderable {
  override def key: `WWW-Authenticate`.type = `WWW-Authenticate`
  type Value = Challenge
}
