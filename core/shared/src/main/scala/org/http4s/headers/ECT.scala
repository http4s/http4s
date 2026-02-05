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

import cats.parse.Parser.string
import org.typelevel.ci._

sealed abstract case class ECT(value: String)

object ECT {
  object `slow-2g` extends ECT("slow-2g")
  object `2g` extends ECT("2g")
  object `3g` extends ECT("3g")
  object `4g` extends ECT("4g")

  private[http4s] val parser = {
    val parser2gSlow = string("slow-2g").as(`slow-2g`)
    val parser2g = string("2g").as(`2g`)
    val parser3g = string("3g").as(`3g`)
    val parser4g = string("4g").as(`4g`)

    parser4g | parser3g | parser2g | parser2gSlow
  }

  def parse(s: String): ParseResult[ECT] = ParseResult.fromParser(parser, "Invalid ECT header")(s)

  implicit val headerInstance: Header[ECT, Header.Single] =
    Header.create(ci"ECT", _.value, parse)
}
