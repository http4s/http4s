/*
 * Copyright (C) 2009-2013 Mathias Doenitz, Alexander Myltsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.internal.parboiled2.nestedpackage

import scala.util.Success
import org.specs2.mutable.Specification

class AlienPackageParserSpec extends Specification {

  abstract class AbstractParser(val input: org.http4s.internal.parboiled2.ParserInput) extends org.http4s.internal.parboiled2.Parser {
    import org.http4s.internal.parboiled2.{ Rule1, CharPredicate }

    def foo: Rule1[String] = rule { capture("foo" ~ zeroOrMore(CharPredicate.Digit)) }
  }

  class FooParser(input: String) extends AbstractParser(input) {
    def Go = rule { foo ~ EOI }
  }

  "Parsers in files that dont explicitly import org.http4s.internal.parboiled2._" should {
    "compile" in {
      new FooParser("foo123").Go.run() === Success("foo123")
    }
  }
}
