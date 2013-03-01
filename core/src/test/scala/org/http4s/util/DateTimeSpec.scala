/*
 * Derived from spray
 * https://github.com/spray/spray/blob/962bbfa75e5a4429a9a27887e4a2fb4a53a4f827/spray-http/src/test/scala/spray/http/DateTimeSpec.scala
 *
 * Copyright (C) 2011-2012 spray.io
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
package org.http4s.util

import java.util.TimeZone
import scala.util.Random
import org.specs2.matcher.Matcher
import org.specs2.mutable._


class DateTimeSpec extends Specification {

  val GMT = TimeZone.getTimeZone("GMT")
  val specificClicks = DateTime(2011, 7, 12, 14, 8, 12).clicks
  val startClicks = DateTime(1800, 1, 1, 0, 0, 0).clicks
  val maxClickDelta = DateTime(2199, 12, 31, 23, 59, 59).clicks - startClicks
  val random = new Random()
  val httpDateTimes = Stream.continually {
    DateTime(startClicks + math.abs(random.nextLong()) % maxClickDelta)
  }

  "DateTime.toRfc1123DateTimeString" should {
    "properly print a known date" in {
      DateTime(specificClicks).toRfc1123DateTimeString mustEqual "Tue, 12 Jul 2011 14:08:12 GMT"
      DateTime(2011, 7, 12, 14, 8, 12).toRfc1123DateTimeString mustEqual "Tue, 12 Jul 2011 14:08:12 GMT"
    }
    "behave exactly as a corresponding formatting via SimpleDateFormat" in {
      val Rfc1123Format = {
        val fmt = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
        fmt.setTimeZone(GMT)
        fmt
      }
      def rfc1123Format(dt: DateTime) = Rfc1123Format.format(new java.util.Date(dt.clicks))
      val matchSimpleDateFormat: Matcher[DateTime] = (
        { (dt: DateTime) => dt.toRfc1123DateTimeString == rfc1123Format(dt) },
        { (dt: DateTime) => dt.toRfc1123DateTimeString + " != " + rfc1123Format(dt) }
        )
      httpDateTimes.take(10000) must matchSimpleDateFormat.forall
    }
  }

  "DateTime.toIsoDateTimeString" should {
    "properly print a known date" in {
      DateTime(specificClicks).toIsoDateTimeString mustEqual "2011-07-12T14:08:12"
    }
  }

  "DateTime.fromIsoDateTimeString" should {
    "properly parse a legal string" in {
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12") must beSome(DateTime(specificClicks))
    }
    "fail on an illegal string" in {
      "example 1" in { DateTime.fromIsoDateTimeString("2011-07-12T14:08:12x") must beNone }
      "example 2" in { DateTime.fromIsoDateTimeString("2011-07-12T14:08_12") must beNone }
      "example 3" in { DateTime.fromIsoDateTimeString("201A-07-12T14:08:12") must beNone }
      "example 4" in { DateTime.fromIsoDateTimeString("2011-13-12T14:08:12") must beNone }
    }
  }

  "The two DateTime implementations" should {
    "allow for transparent round-trip conversions" in {
      def roundTrip(dt: DateTime) = DateTime(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
      val roundTripOk: Matcher[DateTime] = (
        { (dt: DateTime) => val rt = roundTrip(dt); dt == rt && dt.weekday == rt.weekday },
        { (dt: DateTime) => dt.toRfc1123DateTimeString + " != " + roundTrip(dt).toRfc1123DateTimeString }
        )
      httpDateTimes.take(10000) must roundTripOk.forall
    }
    "properly represent DateTime.MinValue" in {
      DateTime.MinValue.toString === "1800-01-01T00:00:00"
      DateTime(DateTime.MinValue.clicks).toString === "1800-01-01T00:00:00"
    }
    "properly represent DateTime.MaxValue" in {
      DateTime.MaxValue.toString === "2199-12-31T23:59:59"
      DateTime(DateTime.MaxValue.clicks).toString === "2199-12-31T23:59:59"
    }
  }
}