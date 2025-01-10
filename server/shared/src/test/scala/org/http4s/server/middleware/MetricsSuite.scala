/*
 * Copyright 2025 http4s.org
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

package org.http4s.server.middleware

import cats.effect.*
import cats.implicits.*
import org.http4s.*
import org.http4s.laws.discipline.arbitrary.http4sTestingArbitraryForResponse
import org.http4s.metrics.{MetricsOps, TerminationType}
import org.http4s.syntax.all.*
import org.scalacheck.effect.PropF.forAllF

class MetricsSuite extends Http4sSuite {
  def createOps(ref: Ref[IO, Int]): MetricsOps[IO] = new MetricsOps[IO] {
    override def increaseActiveRequests(classifier: Option[String]): IO[Unit] =
      ref.update(previous => previous + 1)
    override def decreaseActiveRequests(classifier: Option[String]): IO[Unit] =
      ref.update(previous => previous - 1)
    override def recordHeadersTime(
                                    method: Method,
                                    elapsed: Long,
                                    classifier: Option[String],
                                  ): IO[Unit] = IO.unit
    override def recordTotalTime(
                                  method: Method,
                                  status: Status,
                                  elapsed: Long,
                                  classifier: Option[String],
                                ): IO[Unit] = IO.unit
    override def recordAbnormalTermination(
                                            elapsed: Long,
                                            terminationType: TerminationType,
                                            classifier: Option[String],
                                          ): IO[Unit] = IO.unit
  }

  test("always increment and decrements activeRequests") {
    forAllF(http4sTestingArbitraryForResponse[IO].arbitrary) { (response: Response[IO]) =>
      val routes = HttpRoutes.pure[IO](response)

      val req = Request[IO](uri = uri"/request")
      val res = for {
        ref <- Ref.of[IO, Int](0)
        ops = createOps(ref)
        metrics = Metrics(ops)(routes)
        response <- metrics.apply(req).value
        incrementedCount <- ref.get
        _ <- response.traverse(rs => rs.body.compile.drain)
        decrementedCount <- ref.get
      } yield (incrementedCount, decrementedCount)

      res.assertEquals((1,0))
    }
  }
}
