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
import org.http4s.internal.parsing.Rfc5322
import org.typelevel.ci.CIStringSyntax

sealed trait From extends Product with Serializable {
  val email: String
}

object From extends HeaderCompanion[From]("From") {
  private final case class FromImpl(email: String) extends From {
    override def productPrefix: String = "From"
  }

  override def parse(s: String): ParseResult[From] =
    ParseResult.fromParser(parser, "Invalid From header")(s)

  private[http4s] val parser: Parser[From] = Rfc5322.mailbox.map(email => FromImpl(email))

  implicit val headerInstance: Header[From, Header.Single] = Header.createRendered(
    ci"From",
    _.email,
    parse,
  )
}
