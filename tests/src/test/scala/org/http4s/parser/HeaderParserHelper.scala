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

package org.http4s.parser

import org.http4s.{Header, ParseResult}

trait HeaderParserHelper[H <: Header] {
  def hparse(value: String): ParseResult[H]

  // Also checks to make sure whitespace doesn't effect the outcome
  protected def parse(value: String): H = {
    val a =
      hparse(value).fold(err => sys.error(s"Couldn't parse: '$value'.\nError: $err"), identity)
    val b =
      hparse(value.replace(" ", "")).fold(_ => sys.error(s"Couldn't parse: $value"), identity)
    assert(a == b, "Whitespace resulted in different headers")
    a
  }
}
