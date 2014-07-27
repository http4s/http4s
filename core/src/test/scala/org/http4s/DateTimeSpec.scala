/**
 * Derived from https://raw.githubusercontent.com/spray/akka/53d43846c15969f9e5d36e94570f1fdb6e5bca7d/akka-http-core/src/test/scala/akka/http/util/DateTimeSpec.scala
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package org.http4s

import java.util.TimeZone
import org.specs2.mutable.Specification

import scala.util.Random

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
      DateTime(specificClicks).toRfc1123DateTimeString shouldEqual "Tue, 12 Jul 2011 14:08:12 GMT"
      DateTime(2011, 7, 12, 14, 8, 12).toRfc1123DateTimeString shouldEqual "Tue, 12 Jul 2011 14:08:12 GMT"
    }
    "behave exactly as a corresponding formatting via SimpleDateFormat" in {
      val Rfc1123Format = {
        val fmt = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
        fmt.setTimeZone(GMT)
        fmt
      }
      def rfc1123Format(dt: DateTime) = Rfc1123Format.format(new java.util.Date(dt.clicks))
      foreach(httpDateTimes.take(10000)) { dt: DateTime ⇒
        dt.toRfc1123DateTimeString must_== rfc1123Format(dt)
      }
    }
  }

  "DateTime.toIsoDateTimeString" should {
    "properly print a known date" in {
      DateTime(specificClicks).toIsoDateTimeString shouldEqual "2011-07-12T14:08:12"
    }
  }

  "DateTime.fromIsoDateTimeString" should {
    "properly parse a legal string" in {
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12") should beSome(DateTime(specificClicks))
    }
    "properly parse a legal extended string" in {
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.123Z") should beSome(DateTime(specificClicks))
    }
    "fail on an illegal string" in {
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12x") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08_12") should beNone
      DateTime.fromIsoDateTimeString("201A-07-12T14:08:12") should beNone
      DateTime.fromIsoDateTimeString("2011-13-12T14:08:12") should beNone
    }
    "fail on an illegal extended string" in {
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.a") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.Z") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.1") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.12") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.123") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.1234") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.1Z") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.12Z") should beNone
      DateTime.fromIsoDateTimeString("2011-07-12T14:08:12.1234Z") should beNone
    }
  }

  "The two DateTime implementations" should {
    "allow for transparent round-trip conversions" in {
      def roundTrip(dt: DateTime) = DateTime(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
      foreach(httpDateTimes.take(10000)) { dt: DateTime =>
        val rt = roundTrip(dt);
        dt must_== rt
        dt.weekday must_== rt.weekday
        dt.toRfc1123DateTimeString must_== rt.toRfc1123DateTimeString
      }
    }
    "properly represent DateTime.MinValue" in {
      DateTime.MinValue.toString shouldEqual "1800-01-01T00:00:00"
      DateTime(DateTime.MinValue.clicks).toString shouldEqual "1800-01-01T00:00:00"
    }
    "properly represent DateTime.MaxValue" in {
      DateTime.MaxValue.toString shouldEqual "2199-12-31T23:59:59"
      DateTime(DateTime.MaxValue.clicks).toString shouldEqual "2199-12-31T23:59:59"
    }
  }
}

