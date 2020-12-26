/*
 * Copyright 2016 http4s.org
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
package client
package asynchttpclient

import cats.effect.Sync
import org.asynchttpclient.{ClientStats, HostStats, Response => _}
import scala.jdk.CollectionConverters._

class AsyncHttpClientStats[F[_]: Sync](private val underlying: ClientStats)(implicit F: Sync[F]) {

  def getTotalConnectionCount: F[Long] = F.delay(underlying.getTotalConnectionCount)
  def getTotalActiveConnectionCount: F[Long] = F.delay(underlying.getTotalActiveConnectionCount)
  def getTotalIdleConnectionCount: F[Long] = F.delay(underlying.getTotalIdleConnectionCount)
  def getStatsPerHost: F[Map[String, HostStats]] =
    F.delay(underlying.getStatsPerHost.asScala.toMap)
}
