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
import org.http4s.ServerSentEvent._
import org.http4s.internal.CharPredicate
import org.typelevel.ci._

final case class `Last-Event-Id`(id: EventId)

object `Last-Event-Id` {
  def parse(s: String): ParseResult[`Last-Event-Id`] =
    ParseResult.fromParser(parser, "Invalid Last-Event-Id header")(s)

  private[http4s] val parser = Parser.charsWhile0(CharPredicate.All -- "\n\r").map { (id: String) =>
    `Last-Event-Id`(ServerSentEvent.EventId(id))
  }

  implicit val headerInstance: Header[`Last-Event-Id`, Header.Single] =
    Header.create(
      ci"Last-Event-Id",
      _.id.value,
      parse,
    )

}
