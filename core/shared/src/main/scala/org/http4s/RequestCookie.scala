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

import cats.parse.Parser
import org.http4s.internal.parsing.RelaxedCookies
import org.http4s.util.Renderable
import org.http4s.util.Writer

// see https://datatracker.ietf.org/doc/html/rfc6265
final case class RequestCookie(name: String, content: String) extends Renderable {
  override lazy val renderString: String = super.renderString

  override def render(writer: Writer): writer.type = {
    writer.append(name).append('=').append(content)
    writer
  }
}

object RequestCookie {
  private[http4s] val parser: Parser[RequestCookie] =
    RelaxedCookies.cookiePair.map { case (name, value) =>
      RequestCookie(name, value)
    }
}
