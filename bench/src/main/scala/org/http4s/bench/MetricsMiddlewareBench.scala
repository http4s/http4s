/*
 * Copyright 2015 http4s.org
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

package org.http4s.bench

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.middleware.Metrics
import org.http4s.metrics.MetricsOps
import org.http4s.syntax.all._
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit
import cats.Applicative

// sbt "bench/jmh:run -i 10 -wi 10 -f 2 -t 1 org.http4s.bench.MetricsMiddlewareBench"
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
class MetricsMiddlewareBench {
  import MetricsMiddlewareBench._

  @Param(Array("1024", "10240", "102400", "1048576"))
  var responseBodySize: Int = _

  var client: Client[IO] = _
  var metricsClient: Client[IO] = _
  var request: Request[IO] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val baseClient = Client.fromHttpApp[IO](HttpApp[IO] { _ =>
      IO.pure(Response[IO](status = Status.Ok).withEntity(createBody(responseBodySize)))
    })

    client = baseClient
    metricsClient = Metrics(noOpMetricsOps, classifierF)(baseClient)
    request = Request[IO](Method.GET, uri"/test")
  }

  @Benchmark
  def withoutMetrics(): Unit =
    client.run(request).use(_ => IO.unit).unsafeRunSync()

  @Benchmark
  def withMetrics(): Unit =
    metricsClient.run(request).use(_ => IO.unit).unsafeRunSync()

  @Benchmark
  def withoutMetricsConsumeBody(): Unit =
    client
      .run(request)
      .use(resp => resp.body.compile.drain)
      .unsafeRunSync()

  @Benchmark
  def withMetricsConsumeBody(): Unit =
    metricsClient
      .run(request)
      .use(resp => resp.body.compile.drain)
      .unsafeRunSync()
}

object MetricsMiddlewareBench {
  val classifierF: Request[IO] => Option[String] = _ => Some("test-classifier")

  def createBody(size: Int): Stream[IO, Byte] = {
    val chunkSize = 8192
    val numChunks = (size + chunkSize - 1) / chunkSize
    Stream
      .range(0, numChunks)
      .flatMap { i =>
        val remaining = size - (i * chunkSize)
        val thisChunkSize = Math.min(chunkSize, remaining)
        Stream.chunk(fs2.Chunk.array(new Array[Byte](thisChunkSize)))
      }
  }

  val noOpMetricsOps: MetricsOps[IO] = new MetricsOps[IO] {
    override def increaseActiveRequests(classifier: Option[String]): IO[Unit] = IO.unit
    override def decreaseActiveRequests(classifier: Option[String]): IO[Unit] = IO.unit
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
        terminationType: org.http4s.metrics.TerminationType,
        classifier: Option[String],
    ): IO[Unit] = IO.unit
    override def recordResponseBodySize(
        method: Method,
        status: Status,
        bodySizeBytes: Long,
        classifier: Option[String],
    )(implicit F: Applicative[IO]): IO[Unit] = IO.unit
  }
}
