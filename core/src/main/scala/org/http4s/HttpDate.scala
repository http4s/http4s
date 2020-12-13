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

import cats.Functor
import cats.effect.Clock
import cats.implicits._
import cats.parse.{Parser, Parser1, Rfc5234}
import java.time.{DateTimeException, Instant, ZoneOffset, ZonedDateTime}
import org.http4s.util.{Renderable, Writer}
import scala.concurrent.duration.SECONDS

/** An HTTP-date value represents time as an instance of Coordinated Universal
  * Time (UTC). It expresses time at a resolution of one second.  By using it
  * over java.time.Instant in the model, we assure that if two headers render
  * equally, their values are equal.
  *
  * @see https://tools.ietf.org/html/rfc7231#page65
  */
class HttpDate private (val epochSecond: Long) extends Renderable with Ordered[HttpDate] {
  def compare(that: HttpDate): Int =
    this.epochSecond.compare(that.epochSecond)

  def toInstant: Instant =
    Instant.ofEpochSecond(epochSecond)

  def render(writer: Writer): writer.type =
    writer << toInstant

  override def equals(o: Any): Boolean =
    o match {
      case that: HttpDate => this.epochSecond == that.epochSecond
      case _ => false
    }

  override def hashCode(): Int =
    epochSecond.##
}

object HttpDate {
  private val MinEpochSecond = -2208988800L
  private val MaxEpochSecond = 253402300799L

  /** The earliest value reprsentable as an HTTP-date, `Mon, 01 Jan 1900 00:00:00 GMT`.
    *
    * The minimum year is specified by RFC5322 as 1900.
    *
    * @see https://tools.ietf.org/html/rfc7231#page-65
    * @see https://tools.ietf.org/html/rfc5322#page-14
    */
  val MinValue = HttpDate.unsafeFromEpochSecond(MinEpochSecond)

  /** The latest value reprsentable by RFC1123, `Fri, 31 Dec 9999 23:59:59 GMT`. */
  val MaxValue = HttpDate.unsafeFromEpochSecond(MaxEpochSecond)

  /** Constructs an `HttpDate` from the current time. Starting on January 1,n
    * 10000, this will throw an exception. The author intends to leave this
    * problem for future generations.
    */
  @deprecated("0.20.16", "Use HttpDate.current instead, this breaks referential transparency")
  def now: HttpDate =
    unsafeFromInstant(Instant.now)

  /** Constructs an [[HttpDate]] from the current time. Starting on January 1,n
    * 10000, this will throw an exception. The author intends to leave this
    * problem for future generations.
    */
  def current[F[_]: Functor: Clock]: F[HttpDate] =
    Clock[F].realTime(SECONDS).map(unsafeFromEpochSecond)

  /** The `HttpDate` equal to `Thu, Jan 01 1970 00:00:00 GMT` */
  val Epoch: HttpDate =
    unsafeFromEpochSecond(0)

  /** Parses a date according to RFC7321, Section 7.1.1.1
    *
    * @see https://tools.ietf.org/html/rfc7231#page-65
    */
  def fromString(s: String): ParseResult[HttpDate] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid HTTP date", e.toString)
    }

  /** Like `fromString`, but throws on invalid input */
  def unsafeFromString(s: String): HttpDate =
    fromString(s).fold(throw _, identity)

  /** Constructs a date from the seconds since the [[Epoch]]. If out of range,
    *  returns a ParseFailure.
    */
  def fromEpochSecond(epochSecond: Long): ParseResult[HttpDate] =
    if (epochSecond < MinEpochSecond || epochSecond > MaxEpochSecond)
      ParseResult.fail(
        "Invalid HTTP date",
        s"${epochSecond} out of range for HTTP date. Must be between ${MinEpochSecond} and ${MaxEpochSecond}, inclusive")
    else
      ParseResult.success(new HttpDate(epochSecond))

  /** Like `fromEpochSecond`, but throws any parse failures */
  def unsafeFromEpochSecond(epochSecond: Long): HttpDate =
    fromEpochSecond(epochSecond).fold(throw _, identity)

  /** Constructs a date from an instant, truncating to the most recent second. If
    *  out of range, returns a ParseFailure.
    */
  def fromInstant(instant: Instant): ParseResult[HttpDate] =
    fromEpochSecond(instant.toEpochMilli / 1000)

  /** Like `fromInstant`, but throws any parse failures */
  def unsafeFromInstant(instant: Instant): HttpDate =
    unsafeFromEpochSecond(instant.toEpochMilli / 1000)

  /** Constructs a date from an zoned date-time, truncating to the most recent
    *  second. If out of range, returns a ParseFailure.
    */
  def fromZonedDateTime(dateTime: ZonedDateTime): ParseResult[HttpDate] =
    fromInstant(dateTime.toInstant)

  /** Like `fromZonedDateTime`, but throws any parse failures */
  def unsafeFromZonedDateTime(dateTime: ZonedDateTime): HttpDate =
    unsafeFromInstant(dateTime.toInstant)

  /** `HTTP-date = IMF-fixdate / obs-date` */
  private[http4s] val parser: Parser1[HttpDate] = {
    import Parser.{char, failWith, pure, string1}
    import Rfc5234.{digit, sp}

    def mkHttpDate(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        min: Int,
        sec: Int): Option[HttpDate] =
      try {
        val dt = ZonedDateTime.of(year, month, day, hour, min, sec, 0, ZoneOffset.UTC)
        Some(org.http4s.HttpDate.unsafeFromZonedDateTime(dt))
      } catch {
        case _: DateTimeException =>
          None
      }

    /* day-name     = %x4D.6F.6E ; "Mon", case-sensitive
     *              / %x54.75.65 ; "Tue", case-sensitive
     *              / %x57.65.64 ; "Wed", case-sensitive
     *              / %x54.68.75 ; "Thu", case-sensitive
     *              / %x46.72.69 ; "Fri", case-sensitive
     *              / %x53.61.74 ; "Sat", case-sensitive
     *              / %x53.75.6E ; "Sun", case-sensitive
     */
    val dayName =
      List("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        .map(string1)
        .zipWithIndex
        .map { case (s, i) => string1(s).as(i) }
        .reduceLeft(_.orElse1(_))
        .soft

    /* day          = 2DIGIT */
    val day = (digit ~ digit).string.map(_.toInt)

    /* month        = %x4A.61.6E ; "Jan", case-sensitive
     *              / %x46.65.62 ; "Feb", case-sensitive
     *              / %x4D.61.72 ; "Mar", case-sensitive
     *              / %x41.70.72 ; "Apr", case-sensitive
     *              / %x4D.61.79 ; "May", case-sensitive
     *              / %x4A.75.6E ; "Jun", case-sensitive
     *              / %x4A.75.6C ; "Jul", case-sensitive
     *              / %x41.75.67 ; "Aug", case-sensitive
     *              / %x53.65.70 ; "Sep", case-sensitive
     *              / %x4F.63.74 ; "Oct", case-sensitive
     *              / %x4E.6F.76 ; "Nov", case-sensitive
     *              / %x44.65.63 ; "Dec", case-sensitive
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
        "Dec").zipWithIndex
        .map { case (s, i) => string1(s).as(i + 1) }
        .reduceLeft(_.orElse1(_))

    /* year         = 4DIGIT */
    val year = (digit ~ digit ~ digit ~ digit).string.map(_.toInt)

    /* date1        = day SP month SP year
     *              ; e.g., 02 Jun 1982
     */
    val date1 = (day <* sp) ~ (month <* sp) ~ year

    /* hour         = 2DIGIT */
    val hour = (digit ~ digit).string.map(_.toInt)

    /* minute       = 2DIGIT */
    val minute = (digit ~ digit).string.map(_.toInt)

    /* second       = 2DIGIT */
    val second = (digit ~ digit).string.map(_.toInt)

    val colon = char(':')
    /* time-of-day  = hour ":" minute ":" second
     *              ; 00:00:00 - 23:59:60 (leap second)
     */
    val timeOfDay = (hour <* colon) ~ (minute <* colon) ~ second

    /* IMF-fixdate  = day-name "," SP date1 SP time-of-day SP GMT
     * ; fixed length/zone/capitalization subset of the format
     * ; see Section 3.3 of [RFC5322]
     *
     * GMT          = %x47.4D.54 ; "GMT", case-sensitive
     */
    val imfFixdate =
      ((dayName <* string1(", ")) ~ (date1 <* sp) ~ (timeOfDay <* string1(" GMT"))).mapFilter {
        case ((_, ((day, month), year)), ((hour, min), sec)) =>
          mkHttpDate(year, month, day, hour, min, sec)
      }

    val twoDigit = (digit ~ digit).string.map(_.toInt)

    /* date2        = day "-" month "-" 2DIGIT
     *              ; e.g., 02-Jun-82
     */
    val date2 = (day <* char('-')) ~ (month <* char('-')) ~ twoDigit

    /* day-name-l   = %x4D.6F.6E.64.61.79    ; "Monday", case-sensitive
     *              / %x54.75.65.73.64.61.79       ; "Tuesday", case-sensitive
     *              / %x57.65.64.6E.65.73.64.61.79 ; "Wednesday", case-sensitive
     *              / %x54.68.75.72.73.64.61.79    ; "Thursday", case-sensitive
     *              / %x46.72.69.64.61.79          ; "Friday", case-sensitive
     *              / %x53.61.74.75.72.64.61.79    ; "Saturday", case-sensitive
     *              / %x53.75.6E.64.61.79          ; "Sunday", case-sensitive
     */
    val dayNameL =
      List("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        .map(string1)
        .reduceLeft(_.orElse1(_))

    /* rfc850-date  = day-name-l "," SP date2 SP time-of-day SP GMT
     *
     * "Recipients of a timestamp value in rfc850-date format, which uses a
     * two-digit year, MUST interpret a timestamp that appears to be more
     * than 50 years in the future as representing the most recent year in
     * the past that had the same last two digits."
     *
     * Following that rule would make the parser impure for a case
     * rarely seen in the wild anymore.  We're going to observe it
     * from 2020 and hope our descendants are smarter than us.
     */
    val rfc850Date =
      ((dayNameL <* string1(", ")) ~ (date2 <* sp) ~ (timeOfDay <* string1(" GMT"))).mapFilter {
        case ((_, ((day, month), year)), ((hour, min), sec)) =>
          val wrapYear = if (year < 70) (year + 2000) else (year + 1900)
          mkHttpDate(wrapYear, month, day, hour, min, sec)
      }

    val oneDigit = digit.map(_ - '0')

    /* date3        = month SP ( 2DIGIT / ( SP 1DIGIT )) */
    val date3 = (month <* sp) ~ (twoDigit.orElse1(sp *> oneDigit))

    /* asctime-date = day-name SP date3 SP time-of-day SP year
     *              ; e.g., Jun  2
     */
    val asctimeDate = ((dayName <* sp) ~ (date3 <* sp) ~ (timeOfDay <* sp) ~ year).mapFilter {
      case (((_, (month, day)), ((hour, min), sec)), year) =>
        mkHttpDate(year, month, day, hour, min, sec)
    }

    /* obs-date     = rfc850-date / asctime-date */
    val obsDate = rfc850Date.orElse1(asctimeDate)

    imfFixdate.orElse1(obsDate)
  }

}
