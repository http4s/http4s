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
import org.http4s.Request
import org.http4s.RequestPrelude
import org.http4s.ResponsePrelude

import scala.concurrent.duration.FiniteDuration

final class TestMetricsOps2 private (
    ref: Ref[IO, TestMetricsOps2.State],
    include: MetricsRequest => Boolean,
) extends MetricsOps2[IO] {
  import TestMetricsOps2._

  type Context = Option[String]

  def state: IO[State] = ref.get

  def createContext(request: MetricsRequest): IO[Option[Context]] = {
    val context = Some(request.requestPrelude.method.name)
    if (include(request))
      ref
        .update(s =>
          s.copy(
            contexts = (request.requestPrelude, context) :: s.contexts,
            connectionInfos = request.connectionInfo :: s.connectionInfos,
          )
        )
        .as(Some(context))
    else IO.pure(None)
  }

  def increaseActiveRequests(request: MetricsRequest, context: Context): IO[Unit] =
    ref.update(s =>
      s.copy(active = s.active + 1L, increases = (request.requestPrelude, context) :: s.increases)
    )

  def decreaseActiveRequests(request: MetricsRequest, context: Context): IO[Unit] =
    ref.update(s =>
      s.copy(active = s.active - 1L, decreases = (request.requestPrelude, context) :: s.decreases)
    )

  def recordHeadersTime(
      request: MetricsRequest,
      elapsed: FiniteDuration,
      context: Context,
  ): IO[Unit] =
    ref.update(s => s.copy(headers = (request.requestPrelude, elapsed, context) :: s.headers))

  def recordTotalTime(
      request: MetricsRequest,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      elapsed: FiniteDuration,
      context: Context,
  ): IO[Unit] = ref.update(s =>
    s.copy(
      totals =
        Total(request.requestPrelude, response, terminationType, elapsed, context) :: s.totals
    )
  )

  def recordRequestBodySize(
      request: MetricsRequest,
      response: Option[ResponsePrelude],
      terminationType: Option[TerminationType],
      bodySizeBytes: Long,
      context: Context,
  ): IO[Unit] = ref.update(s =>
    s.copy(
      requestBodies =
        BodySize(request.requestPrelude, response, terminationType, bodySizeBytes, context) ::
          s.requestBodies
    )
  )

  def recordResponseBodySize(
      request: MetricsRequest,
      response: ResponsePrelude,
      terminationType: Option[TerminationType],
      bodySizeBytes: Long,
      context: Context,
  ): IO[Unit] = ref.update(s =>
    s.copy(
      responseBodies = BodySize(
        request.requestPrelude,
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
      connectionInfos: List[Option[Request.Connection]],
      increases: List[(RequestPrelude, Option[String])],
      decreases: List[(RequestPrelude, Option[String])],
      headers: List[(RequestPrelude, FiniteDuration, Option[String])],
      totals: List[Total],
      requestBodies: List[BodySize],
      responseBodies: List[BodySize],
  )

  object State {
    val empty: State = State(0L, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
  }

  def create: IO[TestMetricsOps2] = create(_ => true)

  def create(include: MetricsRequest => Boolean): IO[TestMetricsOps2] =
    Ref.of[IO, State](State.empty).map(new TestMetricsOps2(_, include))
}
