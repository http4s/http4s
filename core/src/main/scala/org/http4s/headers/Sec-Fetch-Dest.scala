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
import org.typelevel.ci._

// https://w3c.github.io/webappsec-fetch-metadata/#sec-fetch-dest-header

abstract class `Sec-Fetch-Dest` private[headers] (val value: String)

object `Sec-Fetch-Dest` {
  case object audio extends `Sec-Fetch-Dest`("audio")
  case object audioworklet extends `Sec-Fetch-Dest`("audioworklet")
  case object document extends `Sec-Fetch-Dest`("document")
  case object embed extends `Sec-Fetch-Dest`("embed")
  case object empty extends `Sec-Fetch-Dest`("empty")
  case object font extends `Sec-Fetch-Dest`("font")
  case object frame extends `Sec-Fetch-Dest`("frame")
  case object iframe extends `Sec-Fetch-Dest`("iframe")
  case object image extends `Sec-Fetch-Dest`("image")
  case object manifest extends `Sec-Fetch-Dest`("manifest")
  case object `object` extends `Sec-Fetch-Dest`("object")
  case object paintworklet extends `Sec-Fetch-Dest`("paintworklet")
  case object report extends `Sec-Fetch-Dest`("report")
  case object script extends `Sec-Fetch-Dest`("script")
  case object serviceworker extends `Sec-Fetch-Dest`("serviceworker")
  case object sharedworker extends `Sec-Fetch-Dest`("sharedworker")
  case object style extends `Sec-Fetch-Dest`("style")
  case object track extends `Sec-Fetch-Dest`("track")
  case object video extends `Sec-Fetch-Dest`("video")
  case object worker extends `Sec-Fetch-Dest`("worker")
  case object xslt extends `Sec-Fetch-Dest`("xslt")

  private[http4s] val types: Map[String, `Sec-Fetch-Dest`] =
    List(
      audio,
      audioworklet,
      document,
      embed,
      empty,
      font,
      frame,
      iframe,
      image,
      manifest,
      `object`,
      paintworklet,
      report,
      script,
      serviceworker,
      sharedworker,
      style,
      track,
      video,
      worker,
      xslt,
    )
      .map(i => (i.value, i))
      .toMap

  private val parser: Parser[`Sec-Fetch-Dest`] =
    Parser.anyChar.rep.string.mapFilter(types.get)

  def parse(s: String): ParseResult[`Sec-Fetch-Dest`] =
    ParseResult.fromParser(parser, "Invalid Sec-Fetch-Dest header")(s)

  def apply(value: `Sec-Fetch-Dest`): `Sec-Fetch-Dest` =
    value

  implicit val headerInstance: Header[`Sec-Fetch-Dest`, Header.Single] =
    Header.createRendered(
      ci"Sec-Fetch-Dest",
      _.value,
      parse,
    )
}
