/*
 * Copyright 2021 http4s.org
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

package org.http4s.parsing

import cats.parse.Parser
import cats.parse.Parser.{char, string}
import cats.parse.Rfc5234.digit

object Rfc7230 {
  /* @see [[https://tools.ietf.org/html/rfc7230#section-2.6 RFC7230, Section 2.6]]*/
  val httpName =
    // HTTP-name = %x48.54.54.50 ; "HTTP", case-sensitive
    string("HTTP")

  /* @see [[https://tools.ietf.org/html/rfc7230#section-2.6 RFC7230, Section 2.6]]*/
  val httpVersion: Parser[(Int, Int)] =
    // HTTP-version = HTTP-name "/" DIGIT "." DIGIT
    httpName *> char('/') *> digit.map(_ - '0') ~ (char('.') *> digit.map(_ - '0'))
}
