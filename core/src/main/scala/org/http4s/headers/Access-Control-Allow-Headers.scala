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

import org.typelevel.ci.CIString
import org.http4s.parser.HttpHeaderParser
import org.http4s.util._
import cats.data.NonEmptyList

object `Access-Control-Allow-Headers`
    extends HeaderKey.Internal[`Access-Control-Allow-Headers`]
    with HeaderKey.Recurring {

  override def parse(s: String): ParseResult[`Access-Control-Allow-Headers`] =
    HttpHeaderParser.ACCESS_CONTROL_ALLOW_HEADERS(s)
}

final case class `Access-Control-Allow-Headers`(values: NonEmptyList[CIString])
    extends Header.RecurringRenderer {
  override type Value = CIString

  override implicit def renderer: Renderer[Value] = Renderer.ciStringRenderer
  override def key: `Access-Control-Allow-Headers`.type = `Access-Control-Allow-Headers`
}
