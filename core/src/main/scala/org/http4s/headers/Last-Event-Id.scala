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

import org.http4s
import org.http4s.ServerSentEvent._
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

final case class `Last-Event-Id`(id: EventId) extends Header.Parsed {
  override def key: http4s.headers.`Last-Event-Id`.type = `Last-Event-Id`
  override def renderValue(writer: Writer): writer.type =
    writer.append(id.value)
}

object `Last-Event-Id` extends HeaderKey.Internal[`Last-Event-Id`] with HeaderKey.Singleton {
  def parse(s: String): ParseResult[`Last-Event-Id`] =
    HttpHeaderParser.LAST_EVENT_ID(s)
}
