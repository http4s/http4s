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

import cats.syntax.all._
import org.http4s.internal.parboiled2._

/** Helper class that produces a `ParseResult` from the `main` target. */
private[http4s] abstract class Http4sParser[A](s: String, failureSummary: String) extends Parser {
  def input = s
  def main: Rule1[A]
  private[this] def target = rule(main ~ EOI)
  def parse =
    __run(target)(Parser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure(failureSummary, e.format(s)))
}
