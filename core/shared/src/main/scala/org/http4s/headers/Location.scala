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

import org.typelevel.ci._

import java.nio.charset.StandardCharsets

object Location {

  def parse(s: String): ParseResult[Location] =
    ParseResult.fromParser(parser, "Invalid Location header")(s)

  private[http4s] val parser = Uri.Parser
    .absoluteUri(StandardCharsets.ISO_8859_1)
    .orElse(Uri.Parser.relativeRef(StandardCharsets.ISO_8859_1))
    .map(Location(_))

  implicit val headerInstance: Header[Location, Header.Single] =
    Header.create(
      ci"Location",
      _.uri.toString,
      parse,
    )

}

final case class Location(uri: Uri)
