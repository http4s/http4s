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

@deprecated(
  "Misspelled, never documented, never mixed into MediaType companion, and obsolete",
  "0.22.2",
)
trait MediaTypePlaform {

  /** Literal syntax for MediaTypes.  Invalid or non-literal arguments are rejected
    * at compile time.
    */
  @deprecated("""use mediaType"" string interpolation instead""", "0.20")
  def mediaType(s: String): MediaType = macro MediaTypePlaform.Macros.mediaTypeLiteral
}

@deprecated("Misspelled, never documented, and obsolete", "0.22.2")
object MediaTypePlaform {
  @deprecated(
    """MediaType literal is deprecated.  Import `org.http4s.implicits._` and use the mediaType"" string context""",
    "0.22.2",
  )
  def deprecatedLiteralImpl(s: String): MediaType =
    MediaType.parse(s).fold(throw _, identity)

  private[MediaTypePlaform] class Macros(val c: whitebox.Context) {
    import c.universe._

    def mediaTypeLiteral(s: c.Expr[String]): Tree =
      s.tree match {
        case Literal(Constant(s: String)) =>
          MediaType
            .parse(s)
            .fold(
              e => c.abort(c.enclosingPosition, e.details),
              _ => q"_root_.org.http4s.MediaTypePlaform.deprecatedLiteralImpl($s)",
            )
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"This method uses a macro to verify that a String literal is a valid media type. Use MediaType.parse if you have a dynamic String that you want to parse as a MediaType.",
          )
      }
  }
}
