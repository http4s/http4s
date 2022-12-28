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

import org.http4s.internal.parsing.CommonRules
import org.typelevel.ci._

object Allow {
  def apply(ms: Method*): Allow = Allow(ms.toSet)

  def parse(s: String): ParseResult[Allow] =
    ParseResult.fromParser(parser, "Invalid Allow header")(s)

  private[http4s] val parser = CommonRules
    .headerRep1(CommonRules.token.mapFilter(s => Method.fromString(s).toOption))
    .map(_.toList)
    .?
    .map(_.getOrElse(Nil))
    .map(ms => Allow(ms.toSet))

  implicit val headerInstance: Header[Allow, Header.Single] =
    Header.createRendered(
      ci"Allow",
      _.methods,
      parse,
    )
}

/** A Response header that lists the methods that are supported by the target resource.
  * Must be attached to responses with status  [[https://datatracker.ietf.org/doc/html/rfc7231#section-6.5.5 405 Not Allowed]],
  * though in practice not all servers honor this.
  *
  * [[https://datatracker.ietf.org/doc/html/rfc7231#section-7.4.1 RFC-7231 Section 7.4.1 Allow]]
  */
final case class Allow(methods: Set[Method])
