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

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

trait UriPlatform {

  /** Literal syntax for URIs.  Invalid or non-literal arguments are rejected
    * at compile time.
    */
  @deprecated("""use uri"" string interpolation instead""", "0.20")
  def uri(s: String): Uri = macro UriPlatform.Macros.uriLiteral
}

object UriPlatform {
  private[UriPlatform] class Macros(val c: whitebox.Context) {
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
