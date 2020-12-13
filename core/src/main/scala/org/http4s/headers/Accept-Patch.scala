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

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Renderer
import cats.data.NonEmptyList

object `Accept-Patch` extends HeaderKey.Internal[`Accept-Patch`] with HeaderKey.Recurring {

  override def parse(s: String): ParseResult[`Accept-Patch`] =
    HttpHeaderParser.ACCEPT_PATCH(s)

}

// see https://tools.ietf.org/html/rfc5789#section-3.1
final case class `Accept-Patch` private (values: NonEmptyList[MediaType])
    extends Header.RecurringRenderer {

  type Value = MediaType
  val renderer = Renderer[MediaType]

  override def key: `Accept-Patch`.type = `Accept-Patch`

}
