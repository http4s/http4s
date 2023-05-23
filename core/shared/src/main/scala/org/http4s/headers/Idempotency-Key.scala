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

package org.http4s.headers

import org.http4s.Header
import org.http4s.ParseResult
import org.http4s.internal.parsing.CommonRules.quotedString
import org.http4s.internal.parsing.CommonRules.token
import org.typelevel.ci._

object `Idempotency-Key` {
  def parse(s: String): ParseResult[`Idempotency-Key`] =
    ParseResult.fromParser(parser, "Invalid Idempotency-Key header")(s)

  private[http4s] val parser =
    token.orElse(quotedString).map(`Idempotency-Key`.apply)

  implicit val headerInstance: Header[`Idempotency-Key`, Header.Single] =
    Header.create(ci"Idempotency-Key", _.key, parse)
}

/** Request header defines request to be idempotent used by client retry middleware.
  *
  *  [[https://datatracker.ietf.org/doc/html/draft-idempotency-header-00#section-2.1 idempotency-header]]
  */
final case class `Idempotency-Key`(key: String)
