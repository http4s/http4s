/*
 * Copyright 2026 http4s.org
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

object TestMetricsOps {

  case class State(
      active: Long,
      headersTime: List[(Method, Long, Option[String])],
      totalTime: List[(Method, Status, Long, Option[String])],
      abnormal: List[(Long, TerminationType, Option[String])],
  )

  object State {
    val empty: State = State(0L, Nil, Nil, Nil)
  }

  // Create a MetricsOps that updates `State` for us to observe in the tests.
  def create: IO[(MetricsOps[IO], IO[State])] =
    Ref.of[IO, State](State.empty).map { ref =>
      val ops: MetricsOps[IO] = new MetricsOps[IO] {
        def increaseActiveRequests(classifier: Option[String]): IO[Unit] =
          ref.update(s => s.copy(active = s.active + 1L))
        def decreaseActiveRequests(classifier: Option[String]): IO[Unit] =
          ref.update(s => s.copy(active = s.active - 1L))
        def recordHeadersTime(
            method: Method,
            elapsed: Long,
            classifier: Option[String],
        ): IO[Unit] =
          ref.update(s => s.copy(headersTime = (method, elapsed, classifier) :: s.headersTime))
        def recordTotalTime(
            method: Method,
            status: Status,
            elapsed: Long,
            classifier: Option[String],
        ): IO[Unit] =
          ref.update(s => s.copy(totalTime = (method, status, elapsed, classifier) :: s.totalTime))
        def recordAbnormalTermination(
            elapsed: Long,
            terminationType: TerminationType,
            classifier: Option[String],
        ): IO[Unit] =
          ref.update(s => s.copy(abnormal = (elapsed, terminationType, classifier) :: s.abnormal))
      }
      (ops, ref.get)
    }
}
