/*
 * Copyright 2019 http4s.org
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
