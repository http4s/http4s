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
package internal

import scala.reflect.macros.whitebox

@deprecated("Supports deprecated macros for binary compatibility. Do not use.", "<DOTTY>")
private[http4s] object DeprecatedMacros {
  class MediaType(val c: whitebox.Context) {
    import c.universe._

    def mediaTypeLiteral(s: c.Expr[String]): Tree =
      s.tree match {
        case Literal(Constant(s: String)) =>
          MediaType
            .parse(s)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              _ =>
                q"_root_.org.http4s.MediaType.parse($s).fold(throw _, _root_.scala.Predef.identity)"
            )
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"This method uses a macro to verify that a String literal is a valid media type. Use MediaType.parse if you have a dynamic String that you want to parse as a MediaType."
          )
      }
  }

  class QValue(val c: whitebox.Context) {
    import c.universe._

    def qValueLiteral(d: c.Expr[Double]): Tree =
      d.tree match {
        case Literal(Constant(d: Double)) =>
          QValue
            .fromDouble(d)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              qValue => q"_root_.org.http4s.QValue.☠(${qValue.thousandths})"
            )
        case _ =>
          c.abort(c.enclosingPosition, s"literal Double value required")
      }
  }

  class Uri(val c: whitebox.Context) {
    import c.universe._

    def uriLiteral(s: c.Expr[String]): Tree =
      s.tree match {
        case Literal(Constant(s: String)) =>
          Uri
            .fromString(s)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              _ =>
                q"_root_.org.http4s.Uri.fromString($s).fold(throw _, _root_.scala.Predef.identity)"
            )
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"This method uses a macro to verify that a String literal is a valid URI. Use Uri.fromString if you have a dynamic String that you want to parse as a Uri."
          )
      }
  }
}
