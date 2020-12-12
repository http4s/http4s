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
package parser

import java.nio.charset.Charset
import org.http4s.{Query => Q}
import org.http4s.internal.parboiled2._

private[http4s] class RequestUriParser(val input: ParserInput, val charset: Charset)
    extends Parser
    with Rfc3986Parser {
  def RequestUri =
    rule {
      (OriginForm |
        AbsoluteUri |
        Authority ~> (auth => org.http4s.Uri(authority = Some(auth))) |
        Asterisk) ~ EOI
    }

  def OriginForm =
    rule {
      PathAbsolute ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) ~> {
        (path, query, fragment) =>
          val q = query.map(Q.fromString).getOrElse(Q.empty)
          org.http4s.Uri(path = path, query = q, fragment = fragment)
      }
    }

  def Asterisk: Rule1[Uri] =
    rule {
      "*" ~ push(
        org.http4s.Uri(
          authority = Some(org.http4s.Uri.Authority(host = org.http4s.Uri.RegName("*"))),
          path = ""))
    }
}
