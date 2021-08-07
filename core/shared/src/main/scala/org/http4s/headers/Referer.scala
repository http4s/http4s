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

import java.nio.charset.StandardCharsets
import org.typelevel.ci._

object Referer {

  def parse(s: String): ParseResult[Referer] =
    ParseResult.fromParser(parser, "Invalid Referer header")(s)

  private[http4s] val parser = Uri.Parser
    .absoluteUri(StandardCharsets.ISO_8859_1)
    .orElse(Uri.Parser.relativeRef(StandardCharsets.ISO_8859_1))
    .map(Referer(_))

  implicit val headerInstance: Header[Referer, Header.Single] =
    Header.createRendered(
      ci"Referer",
      _.uri,
      parse
    )

}

final case class Referer(uri: Uri)
