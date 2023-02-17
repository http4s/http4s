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

import org.http4s.Header
import org.http4s.ParseFailure
import org.http4s.Uri
import org.typelevel.ci._

object `X-Forwarded-Host` {

  def parse(headerValue: String): Either[ParseFailure, `X-Forwarded-Host`] =
    ParseResult.fromParser(
      Uri.Parser.host.map(`X-Forwarded-Host`.apply),
      "Invalid X-Forwarded-Host header",
    )(headerValue)

  implicit val headerInstance: Header[`X-Forwarded-Host`, Header.Single] =
    Header.create(
      ci"X-Forwarded-Host",
      _.host.toString(),
      parse,
    )

}

final case class `X-Forwarded-Host`(host: Uri.Host)
