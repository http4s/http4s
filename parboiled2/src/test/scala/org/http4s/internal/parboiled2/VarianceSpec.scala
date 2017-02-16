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

package org.http4s.internal.parboiled2

import org.http4s.illTyped
import support._

//// pure compile-time-only test
class VarianceSpec {

  // the Parsing DSL should
  {

    // honor contravariance on the 1st type param of the `Rule` type
    {
      // valid example
      test {
        abstract class Par extends Parser {
          def A: Rule2[String, Int] = ???
          def B: PopRule[Any :: HNil] = ???
          def C: Rule1[String] = rule { A ~ B }
        }
        ()
      }

      // TODO: fix https://github.com/sirthias/parboiled2/issues/172 and re-enable
//       //invalid example 1
//      test {
//        abstract class Par extends Parser {
//          def A: Rule1[Any] = ???
//          def B: PopRule[Int :: HNil] = ???
//        }
//        illTyped("""class P extends Par { def C = rule { A ~ B } }""", "Illegal rule composition")
//      }

      // invalid example 2
      test {
        abstract class Par extends Parser {
          def A: Rule2[String, Any] = ???
          def B: PopRule[Int :: HNil] = ???
        }
        illTyped("""class P extends Par { def C = rule { A ~ B } }""", "Illegal rule composition")
      }

      // invalid example 3
      test {
        abstract class Par extends Parser {
          def A: Rule1[String] = ???
          def B: PopRule[Int :: HNil] = ???
        }
        illTyped("""class P extends Par { def C = rule { A ~ B } }""", "Illegal rule composition")
      }
    }

    // honor covariance on the 2nd type param of the `Rule` type
    {
      // valid example
      test {
        abstract class Par extends Parser {
          def A: Rule0 = ???
          def B: Rule1[Int] = ???
          def C: Rule1[Any] = rule { A ~ B }
        }
      }

      // invalid example
      test {
        abstract class Par extends Parser {
          def A: Rule0 = ???
          def B: Rule1[Any] = ???
        }
        illTyped("""class P extends Par { def C: Rule1[Int] = rule { A ~ B } }""", "type mismatch;.*")
      }
    }
  }

  def test(x: Any): Unit = () // prevent "a pure expression does nothing in statement position" warnings
}
