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

package org.http4s.internal.parsing

import cats.parse.Parser
import cats.parse.Parser.char
import cats.parse.Parser.charIn
import cats.parse.Parser.string
import cats.parse.Rfc5234.digit
import cats.parse.Rfc5234.sp

/** Common rules defined in RFC2616.  This RFC is now obsolete,
  * but some other active ones still refer to it.
  *
  * @see [[https://datatracker.ietf.org/doc/html/rfc2616#section-2.2] RFC2616, Basic Rules]
  */
private[http4s] object Rfc2616 {
  /* CHAR           = <any US-ASCII character (octets 0 - 127)>
   * CTL            = <any US-ASCII control character
   *                  (octets 0 - 31) and DEL (127)>
   * separators     = "(" | ")" | "<" | ">" | "@"
   *                | "," | ";" | ":" | "\" | <">
   *                | "/" | "[" | "]" | "?" | "="
   *                | "{" | "}" | SP  | HT
   * token          = 1*<any CHAR except CTLs or separators>
   */
  val token: Parser[String] = {
    val ascii = Set(0.toChar to 127.toChar: _*)
    val ctl = Set(0.toChar to 31.toChar: _*) + 127.toChar
    val separators = Set("()<>@,;:\\\"/[]?={} \t".toSeq: _*)

    val tchar = charIn(ascii -- ctl -- separators)
    tchar.rep.string
  }

  final case class Rfc1123Date(
      wkday: Int,
      day: Int,
      month: Int,
      year: Int,
      hour: Int,
      min: Int,
      sec: Int,
  )

  val rfc1123Date: Parser[Rfc1123Date] = {
    /* wkday        = "Mon" | "Tue" | "Wed"
     *              | "Thu" | "Fri" | "Sat" | "Sun"
     */
    val wkday = List("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").zipWithIndex
      .map { case (s, i) => string(s).as(i) }
      .reduceLeft(_.orElse(_))

    val twoDigit = (digit ~ digit).string.map(_.toInt)

    val fourDigit = (digit ~ digit ~ digit ~ digit).string.map(_.toInt)

    /* month        = "Jan" | "Feb" | "Mar" | "Apr"
     *              | "May" | "Jun" | "Jul" | "Aug"
     *              | "Sep" | "Oct" | "Nov" | "Dec"
     */
    val month =
      List(
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
      ).zipWithIndex
        .map { case (s, i) => string(s).as(i + 1) }
        .reduceLeft(_.orElse(_))

    /* date1        = 2DIGIT SP month SP 4DIGIT
     *              ; day month year (e.g., 02 Jun 1982)
     */
    val date1 = (twoDigit <* sp) ~ (month <* sp) ~ fourDigit

    /* time         = 2DIGIT ":" 2DIGIT ":" 2DIGIT
     *              ; 00:00:00 - 23:59:59
     */
    val time = (twoDigit <* char(':')) ~ (twoDigit <* char(':')) ~ twoDigit

    /* rfc1123-date = wkday "," SP date1 SP time SP "GMT" */
    ((wkday <* string(", ")) ~ (date1 <* sp) ~ (time <* string(" GMT"))).map {
      case ((wkday, ((day, month), year)), ((hour, min), sec)) =>
        Rfc1123Date(wkday, day, month, year, hour, min, sec)
    }
  }
}
