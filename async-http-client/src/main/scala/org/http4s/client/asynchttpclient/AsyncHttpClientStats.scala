/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.client.asynchttpclient

import cats.effect.Sync
import org.asynchttpclient.{ClientStats, HostStats}

import scala.jdk.CollectionConverters.MapHasAsScala

class AsyncHttpClientStats[F[_]: Sync](private val underlying: ClientStats)(implicit F: Sync[F]) {

  def getTotalConnectionCount: F[Long] = F.delay(underlying.getTotalConnectionCount)
  def getTotalActiveConnectionCount: F[Long] = F.delay(underlying.getTotalActiveConnectionCount)
  def getTotalIdleConnectionCount: F[Long] = F.delay(underlying.getTotalIdleConnectionCount)
  def getStatsPerHost: F[Map[String, HostStats]] =
    F.delay(underlying.getStatsPerHost.asScala.toMap)
}
