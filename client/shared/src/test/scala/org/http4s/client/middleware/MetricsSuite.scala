/*
 * Copyright 2014 http4s.org
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
package middleware

import cats.effect.IO
import cats.effect.Ref
import org.http4s.dsl.io._
import org.http4s.metrics.MetricsOps
import org.http4s.metrics.TerminationType
import org.http4s.syntax.all._

class MetricsSuite extends Http4sSuite {

  case class RecordedBodySize(
      method: Method,
      status: Status,
      bodySizeBytes: Long,
      classifier: Option[String],
  )

  def recordingMetricsOps(
      bodySizeRef: Ref[IO, List[RecordedBodySize]]
  ): MetricsOps[IO] =
    new MetricsOps[IO] {
      def increaseActiveRequests(classifier: Option[String]): IO[Unit] = IO.unit
      def decreaseActiveRequests(classifier: Option[String]): IO[Unit] = IO.unit
      def recordHeadersTime(
          method: Method,
          elapsed: Long,
          classifier: Option[String],
      ): IO[Unit] = IO.unit
      def recordTotalTime(
          method: Method,
          status: Status,
          elapsed: Long,
          classifier: Option[String],
      ): IO[Unit] = IO.unit
      def recordAbnormalTermination(
          elapsed: Long,
          terminationType: TerminationType,
          classifier: Option[String],
      ): IO[Unit] = IO.unit
      def recordResponseBodySize(
          method: Method,
          status: Status,
          bodySizeBytes: Long,
          classifier: Option[String],
      ): IO[Unit] =
        bodySizeRef.update(_ :+ RecordedBodySize(method, status, bodySizeBytes, classifier))
    }

  private val testApp = HttpApp[IO] {
    case GET -> Root / "test" =>
      Ok("Hello, World!")
    case POST -> Root / "upload" =>
      Ok(fs2.Stream.emit[IO, Byte](0).repeat.take(10240))
    case _ =>
      NotFound()
  }

  test("Client Metrics middleware should record response body size when body is consumed") {
    val bodyBytes = "Hello, World!".getBytes()
    val expectedSize = bodyBytes.length.toLong

    for {
      bodySizeRef <- Ref.of[IO, List[RecordedBodySize]](List.empty)
      ops = recordingMetricsOps(bodySizeRef)

      metricsClient = Metrics(ops, (_: Request[IO]) => Some("test"))(
        Client.fromHttpApp(testApp)
      )
      req = Request[IO](Method.GET, uri"/test")

      _ <- metricsClient.run(req).use(resp => resp.body.compile.drain)
      recorded <- bodySizeRef.get
    } yield {
      assertEquals(recorded.length, 1, "Should record exactly one body size metric")
      assertEquals(recorded.head.method, Method.GET)
      assertEquals(recorded.head.status, Status.Ok)
      assertEquals(recorded.head.bodySizeBytes, expectedSize)
      assertEquals(recorded.head.classifier, Some("test"))
    }
  }

  test("Client Metrics middleware should record correct body size for streamed response") {
    val expectedBodySize = 10240L

    for {
      bodySizeRef <- Ref.of[IO, List[RecordedBodySize]](List.empty)
      ops = recordingMetricsOps(bodySizeRef)

      metricsClient = Metrics(ops)(Client.fromHttpApp(testApp))
      req = Request[IO](Method.POST, uri"/upload")

      _ <- metricsClient.run(req).use(resp => resp.body.compile.drain)
      recorded <- bodySizeRef.get
    } yield {
      assertEquals(recorded.length, 1)
      assertEquals(recorded.head.bodySizeBytes, expectedBodySize)
      assertEquals(recorded.head.method, Method.POST)
    }
  }

  test("Client Metrics middleware should not record body size if body is not consumed") {
    for {
      bodySizeRef <- Ref.of[IO, List[RecordedBodySize]](List.empty)
      ops = recordingMetricsOps(bodySizeRef)

      metricsClient = Metrics(ops)(Client.fromHttpApp(testApp))
      req = Request[IO](Method.GET, uri"/test")

      _ <- metricsClient.run(req).use(_ => IO.unit)
      recorded <- bodySizeRef.get
    } yield assertEquals(
      recorded.length,
      0,
      "Should not record body size when body is not consumed",
    )
  }
}
