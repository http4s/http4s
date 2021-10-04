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
import org.http4s.util.{Renderable, Writer}
import org.http4s.internal.parsing.Rfc7230

/** Directives for the Clear-Site-Data header
  * See https://www.w3.org/TR/clear-site-data/
  */
sealed abstract class SiteData(val value: String) extends Renderable {
  override def render(writer: Writer): writer.type =
    writer.append(s""""$value"""")
}

object SiteData {
  case object `*` extends SiteData("*")
  case object cache extends SiteData("cache")
  case object cookies extends SiteData("cookies")
  case object storage extends SiteData("storage")
  case object executionContexts extends SiteData("executionContexts")

  private val types: Map[String, SiteData] =
    List(`*`, cache, cookies, storage, executionContexts)
      .map(i => (i.value.toLowerCase, i))
      .toMap

  private[http4s] val parser: Parser[SiteData] =
    Rfc7230.quotedString.mapFilter(s => types.get(s.toLowerCase))

  def parse(s: String): ParseResult[SiteData] =
    ParseResult.fromParser(parser, "Invalid SiteData")(s)
}
