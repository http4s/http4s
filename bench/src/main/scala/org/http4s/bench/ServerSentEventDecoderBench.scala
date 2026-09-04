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

import cats.Id
import fs2.Stream
import org.http4s.ServerSentEvent
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import ServerSentEventDecoderBench.sampleLine

@State(Scope.Benchmark)
class ServerSentEventDecoderBench {

  final val LINES = 1000

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OperationsPerInvocation(LINES)
  def serverSentEventDecoder(bh: Blackhole): Unit = {
    val input =
      Stream.repeatEval[Id, Array[Byte]](sampleLine).take(LINES).flatMap(s => Stream.emits(s))
    val ret = ServerSentEvent.decoder[Id](input).compile.count
    assert(ret == LINES + 1)
    bh.consume(ret)
  }

}

object ServerSentEventDecoderBench {
  val sampleLine: Array[Byte] =
    s"data: ${"a" * 100000}\nevent: live\nid: 1727268457046.11\n\n".getBytes()
}
