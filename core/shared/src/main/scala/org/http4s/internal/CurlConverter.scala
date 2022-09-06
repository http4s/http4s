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

import org.http4s.Headers
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.typelevel.ci.CIString

private[http4s] object CurlConverter {

  // escapes characters that are used in the curl-command, such as '
  private def escapeQuotationMarks(s: String) = s.replaceAll("'", """'\\''""")

  private def newline: String = " \\\n  "

  private def prepareMethodName(method: Method): String =
    s"$newline--request ${method.name}"

  private def prepareUri(uri: Uri): String =
    s"$newline--url '${escapeQuotationMarks(uri.renderString)}'"

  private def prepareHeaders(headers: Headers, redactHeadersWhen: CIString => Boolean): String = {
    val preparedHeaders = headers
      .redactSensitive(redactHeadersWhen)
      .headers
      .map { header =>
        s"""--header '${escapeQuotationMarks(s"${header.name}: ${header.value}")}'"""
      }
      .mkString(newline)

    if (preparedHeaders.isEmpty) "" else newline + preparedHeaders
  }

  def requestToCurlWithoutBody[F[_]](
      request: Request[F],
      redactHeadersWhen: CIString => Boolean,
  ): String = {
    val params = List(
      prepareMethodName(request.method),
      prepareUri(request.uri),
      prepareHeaders(request.headers, redactHeadersWhen),
    ).mkString

    s"curl$params"
  }
}
