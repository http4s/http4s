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

import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Writer
import org.typelevel.ci.CIString

object Allow extends HeaderKey.Internal[Allow] with HeaderKey.Singleton {
  def apply(ms: Method*): Allow = Allow(ms.toSet)

  override def parse(s: String): ParseResult[Allow] =
    ParseResult.fromParser(parser, "Invalid Allow")(s)
  private[http4s] val parser = Rfc7230
    .headerRep1(Rfc7230.token.mapFilter(s => Method.fromString(s).toOption))
    .map(_.toList)
    .?
    .map(_.getOrElse(Nil))
    .map(ms => Allow(ms.toSet))

  implicit val headerInstance: v2.Header[Allow, v2.Header.Single] =
    v2.Header.create(
      CIString("Allow"),
      v => ???,
      ParseResult.fromParser(parser, "Invalid Allow")
    )
}

/** A Response header that lists the methods that are supported by the target resource.
  * Often attached to responses with status  [[https://tools.ietf.org/html/rfc7231#section-6.5.5 405 Not Allowed]].
  *
  * [[https://tools.ietf.org/html/rfc7231#section-7.4.1 RFC-7231 Section 7.4.1 Allow]]
  */
final case class Allow(methods: Set[Method]) extends Header.Parsed {
  override def key: Allow.type = Allow
  override def renderValue(writer: Writer): writer.type =
    writer.addSet[Method](methods, sep = ", ")
}
