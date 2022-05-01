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

import scala.reflect.macros.whitebox

trait QValuePlatform {

  /** Supports a literal syntax for validated QValues.
    *
    * Example:
    * {{{
    * q(0.5).success == QValue.fromDouble(0.5)
    * q(1.1) // does not compile: out of range
    * val d = 0.5
    * q(d) // does not compile: not a literal
    * }}}
    */
  @deprecated("""use qValue"" string interpolation instead""", "0.20")
  def q(d: Double): QValue = macro QValuePlatform.Macros.qValueLiteral

}

object QValuePlatform {
  private[QValuePlatform] class Macros(val c: whitebox.Context) {
    import c.universe._

    def qValueLiteral(d: c.Expr[Double]): Tree =
      d.tree match {
        case Literal(Constant(d: Double)) =>
          QValue
            .fromDouble(d)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              qValue => q"_root_.org.http4s.QValue.☠(${qValue.thousandths})",
            )
        case _ =>
          c.abort(c.enclosingPosition, s"literal Double value required")
      }
  }

}
