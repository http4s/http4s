/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/SimpleHeaders.scala
 *
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s
package parser

import org.http4s.headers._

/** parser rules for all headers that can be parsed with one simple rule
  */
private[parser] trait SimpleHeaders {
  def CONTENT_DISPOSITION(value: String): ParseResult[`Content-Disposition`] =
    new Http4sHeaderParser[`Content-Disposition`](value) {
      def entry =
        rule {
          Token ~ zeroOrMore(";" ~ OptWS ~ Parameter) ~ EOL ~> {
            (token: String, params: collection.Seq[(String, String)]) =>
              `Content-Disposition`(token, params.toMap)
          }
        }
    }.parse

  def LAST_EVENT_ID(value: String): ParseResult[`Last-Event-Id`] =
    new Http4sHeaderParser[`Last-Event-Id`](value) {
      def entry =
        rule {
          capture(zeroOrMore(ANY)) ~ EOL ~> { (id: String) =>
            `Last-Event-Id`(ServerSentEvent.EventId(id))
          }
        }
    }.parse
}
