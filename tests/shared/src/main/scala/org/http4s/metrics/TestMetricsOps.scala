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

package org.http4s.metrics

import cats.effect.IO
import cats.effect.Ref
import org.http4s.Method
import org.http4s.Status

/** MetricsOps that accumulates every sample it is handed, so tests can assert on exactly what
  * a middleware recorded.
  */
final class TestMetricsOps private (ref: Ref[IO, TestMetricsOps.State]) extends MetricsOps[IO] {
  import TestMetricsOps._

  /** The samples recorded so far, in the order they were recorded. */
  def state: IO[State] = ref.get.map(_.chronological)

  def increaseActiveRequests(classifier: Option[String]): IO[Unit] =
    ref.update(s => s.copy(active = s.active + 1L))

  def decreaseActiveRequests(classifier: Option[String]): IO[Unit] =
    ref.update(s => s.copy(active = s.active - 1L))

  def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): IO[Unit] =
    ref.update(s => s.copy(headersTime = HeadersTime(method, elapsed, classifier) :: s.headersTime))

  def recordTotalTime(
      method: Method,
      status: Status,
      elapsed: Long,
      classifier: Option[String],
  ): IO[Unit] =
    ref.update(s =>
      s.copy(totalTime = TotalTime(method, status, elapsed, classifier) :: s.totalTime)
    )

  def recordAbnormalTermination(
      elapsed: Long,
      terminationType: TerminationType,
      classifier: Option[String],
  ): IO[Unit] =
    ref.update(s =>
      s.copy(abnormal = AbnormalTermination(elapsed, terminationType, classifier) :: s.abnormal)
    )
}

object TestMetricsOps {

  final case class HeadersTime(method: Method, elapsed: Long, classifier: Option[String])

  final case class TotalTime(
      method: Method,
      status: Status,
      elapsed: Long,
      classifier: Option[String],
  )

  final case class AbnormalTermination(
      elapsed: Long,
      terminationType: TerminationType,
      classifier: Option[String],
  )

  final case class State(
      active: Long,
      headersTime: List[HeadersTime],
      totalTime: List[TotalTime],
      abnormal: List[AbnormalTermination],
  ) {

    /** The statuses passed to `recordTotalTime`, i.e. what backends label their request counter
      * with, since that's where they increment it.
      */
    def statuses: List[Status] = totalTime.map(_.status)

    def terminationTypes: List[TerminationType] = abnormal.map(_.terminationType)

    // Samples are prepended as they arrive, so undo that before handing the state to a test.
    private[TestMetricsOps] def chronological: State =
      State(active, headersTime.reverse, totalTime.reverse, abnormal.reverse)
  }

  object State {
    val empty: State = State(0L, Nil, Nil, Nil)
  }

  def create: IO[TestMetricsOps] =
    Ref.of[IO, State](State.empty).map(new TestMetricsOps(_))
}
