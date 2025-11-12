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

package org.http4s
package metrics

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all._

class ResponseBodyMetricsSuite extends Http4sSuite {

  test("MetricsOps should have a recordResponseBodySize method") {
    case class RecordedMetric(
        method: Method,
        status: Status,
        bodySizeBytes: Long,
        classifier: Option[String],
    )

    for {
      recorded <- Ref.of[IO, List[RecordedMetric]](List.empty)
      ops = new MetricsOps[IO] {
        def increaseActiveRequests(classifier: Option[String]): IO[Unit] = IO.unit
        def decreaseActiveRequests(classifier: Option[String]): IO[Unit] = IO.unit
        def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): IO[Unit] =
          IO.unit
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
          recorded.update(_ :+ RecordedMetric(method, status, bodySizeBytes, classifier))
      }

      _ <- ops.recordResponseBodySize(Method.GET, Status.Ok, 1024L, Some("test"))
      results <- recorded.get
    } yield {
      assertEquals(results.length, 1)
      assertEquals(results.head.method, Method.GET)
      assertEquals(results.head.status, Status.Ok)
      assertEquals(results.head.bodySizeBytes, 1024L)
      assertEquals(results.head.classifier, Some("test"))
    }
  }
}
