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
import org.http4s.RequestPrelude
import org.http4s.ResponsePrelude

import scala.concurrent.duration.FiniteDuration

final class TestMetricsOps2 private (ref: Ref[IO, TestMetricsOps2.State]) extends MetricsOps2[IO] {
  import TestMetricsOps2._

  type Context = Option[String]

  def state: IO[State] = ref.get

  def createContext(request: RequestPrelude): IO[Context] = {
    val context = Some(request.method.name)
    ref.update(s => s.copy(contexts = (request, context) :: s.contexts)).as(context)
  }

  def increaseActiveRequests(request: RequestPrelude, context: Context): IO[Unit] =
    ref.update(s => s.copy(active = s.active + 1L, increases = (request, context) :: s.increases))

  def decreaseActiveRequests(request: RequestPrelude, context: Context): IO[Unit] =
    ref.update(s => s.copy(active = s.active - 1L, decreases = (request, context) :: s.decreases))

  def recordHeadersTime(
      request: RequestPrelude,
      elapsed: FiniteDuration,
      context: Context,
  ): IO[Unit] = ref.update(s => s.copy(headers = (request, elapsed, context) :: s.headers))

  def recordTotalTime(
      request: RequestPrelude,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      context: Context,
  ): IO[Unit] = ref.update(s =>
    s.copy(totals = Total(request, response, terminationType, elapsed, context) :: s.totals)
  )

  def recordRequestBodySize(
      request: RequestPrelude,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      bodySizeBytes: Long,
      context: Context,
  ): IO[Unit] = ref.update(s =>
    s.copy(
      requestBodies =
        BodySize(request, response, terminationType, bodySizeBytes, context) :: s.requestBodies
    )
  )

  def recordResponseBodySize(
      request: RequestPrelude,
      response: ResponsePrelude,
      terminationType: Option[TerminationType],
      bodySizeBytes: Long,
      context: Context,
  ): IO[Unit] = ref.update(s =>
    s.copy(
      responseBodies = BodySize(
        request,
        Some(response),
        terminationType,
        bodySizeBytes,
        context,
      ) :: s.responseBodies
    )
  )
}

object TestMetricsOps2 {
  final case class Total(
      request: RequestPrelude,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      context: Option[String],
  )

  final case class BodySize(
      request: RequestPrelude,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      bodySizeBytes: Long,
      context: Option[String],
  )

  final case class State(
      active: Long,
      contexts: List[(RequestPrelude, Option[String])],
      increases: List[(RequestPrelude, Option[String])],
      decreases: List[(RequestPrelude, Option[String])],
      headers: List[(RequestPrelude, FiniteDuration, Option[String])],
      totals: List[Total],
      requestBodies: List[BodySize],
      responseBodies: List[BodySize],
  )

  object State {
    val empty: State = State(0L, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
  }

  def create: IO[TestMetricsOps2] = Ref.of[IO, State](State.empty).map(new TestMetricsOps2(_))
}
