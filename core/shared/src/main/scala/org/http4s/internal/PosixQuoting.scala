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

/** Mostly POSIX compatible shell quoting.
  *
  * This does rely on Dollar-Single-Quoting for some things, however
  * this is about the only reasonable way to encode some characters, and
  * it has been accepted for inclusion, so it's probably fine.
  *
  * @see https://pubs.opengroup.org/onlinepubs/9699919799/utilities/V3_chap02.html#tag_18_02_02
  * @see https://austingroupbugs.net/view.php?id=249
  */
private[http4s] object PosixQuoting {

  /** Shell quoting is weird when you have to quote quotation marks, here's a quick primer.
    *
    * If you want the literal string {{{<'>}}}, this needs to be quoted as {{{'<'\''>'}}}
    *
    * This becomes {{{'<'}}} followed by the unquoted {{{\'}}} followed by {{{'>'}}}, which the
    * shell then concatenates together to get the desired string {{{<'>}}}
    */
  def singleQuote(s: String): String = s"'${s.replaceAll("'", """'\\''""")}'"

  /** Including control characters in shell literals has been a pain, and there was
    * no good way to do this until Dollar-Single-Quotes was accepted for the next version of
    * POSIX (current version at time of writing is POSIX.1-2017).
    */
  def dollarSingleQuote(s: String): String = {
    // There's probably a better way of doing this.
    def hexify(c: Char): String =
      if (!c.isControl) c.toString
      else f"\\u${c.toInt}%04x"

    val quoted = s.flatMap { c =>
      DollarQuoteReplacements.getOrElse(c, hexify(c))
    }
    s"$$'$quoted'"
  }

  def quote(s: String): String =
    if (needsAdvancedQuoting(s)) dollarSingleQuote(s) else singleQuote(s)

  // Most shells should be OK with unicode glyphs, control chars and certain types of whitespace
  // should be the only problematic ones
  def needsAdvancedQuoting(s: String): Boolean =
    s.exists(c => c.isControl || needsDollarQuoteReplacement(c))

  // See https://en.wikipedia.org/wiki/ASCII for name to escape conventions, since most
  // everyone uses those anyway
  private val DollarQuoteReplacements: Map[Char, String] =
    Map(
      '\'' -> "\'",
      '\\' -> "\\\\",
      '\u0007' -> "\\a",
      '\u0008' -> "\\b",
      '\u001B' -> "\\e",
      '\u000C' -> "\\f",
      '\n' -> "\n", // This one doesn't get switched because not encoding it makes the output easier to read
      '\r' -> "\\r",
      '\t' -> "\\t",
      '\u000B' -> "\\v",
    )

  private val needsDollarQuoteReplacement =
    DollarQuoteReplacements.keySet - '\'' - '\n'
}
