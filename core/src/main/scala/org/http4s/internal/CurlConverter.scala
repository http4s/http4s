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

package org.http4s.internal

import org.http4s.Request
import org.typelevel.ci.CIString

private[http4s] object CurlConverter {

  // escapes characters that are used in the curl-command, such as '
  private def escapeQuotationMarks(s: String) = s.replaceAll("'", """'\\''""")

  def requestToCurlWithoutBody[F[_]](request: Request[F], redactHeadersWhen: CIString => Boolean): String = {
    val elements = List(
      s"-X ${request.method.name}",
      s"'${escapeQuotationMarks(request.uri.renderString)}'",
      request.headers
        .redactSensitive(redactHeadersWhen)
        .headers
        .map { header =>
          s"""-H '${escapeQuotationMarks(s"${header.name}: ${header.value}")}'"""
        }
        .mkString(" "),
    )

    s"curl ${elements.filter(_.nonEmpty).mkString(" ")}"
  }
}
