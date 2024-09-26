package org.http4s.bench

import fs2.Stream
import org.http4s.ServerSentEvent

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import cats.Id
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
  val sampleLine = s"data: ${"a" * 100000}\nevent: live\nid: 1727268457046.11\n\n".getBytes()
}
