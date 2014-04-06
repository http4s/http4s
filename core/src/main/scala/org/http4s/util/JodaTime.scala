package org.http4s.util

import org.joda.time.{DateTime, DateTimeZone, ReadableInstant}
import org.joda.time.format.DateTimeFormat
import java.util.Locale

trait JodaTimeInstances {
  val UnixEpoch = new DateTime(0)
}

trait JodaTimeSyntax {
  implicit class ReadableInstantOps(instant: ReadableInstant) {
    import ReadableInstantOps._
    def formatRfc1123: String = Rfc1123Format.print(instant)
  }

  object ReadableInstantOps {
    val Rfc1123Format = DateTimeFormat
      .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
      .withLocale(Locale.US)
      .withZone(DateTimeZone.UTC)
  }
}

object jodaTime extends JodaTimeInstances with JodaTimeSyntax
