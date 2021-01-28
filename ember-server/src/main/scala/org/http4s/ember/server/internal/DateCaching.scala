package org.http4s.ember.server.internal

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.Locale
import cats.effect.Sync
import org.http4s.Header.Raw
import org.http4s.implicits._
import org.http4s.Header

trait DateCaching[F[_]] {
  def getDate: F[Header]
}

object DateCaching {

  private case class CachedDateHeader(acquired: Long, header: Raw)
  private val dateFormat =
    DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
      .withLocale(Locale.US)
      .withZone(ZoneId.of("GMT"))

  private val date = "date".ci

  def impl[F[_]: Sync]: F[DateCaching[F]] = Sync[F].delay {
    new DateCaching[F] {
      // STOLEN From Blaze Is This good?
      @volatile
      var dateTime = CachedDateHeader(0L, Raw(date, dateFormat.format(Instant.now())))

      def getDate: F[Header] = Sync[F].delay {
        val cached = dateTime
        val current = System.currentTimeMillis()
        if (current - cached.acquired <= 1000) cached.header
        else {
          val next = Raw(date, dateFormat.format(Instant.now()))
          dateTime = CachedDateHeader(current, next)
          next
        }
      }
    }
  }
}
