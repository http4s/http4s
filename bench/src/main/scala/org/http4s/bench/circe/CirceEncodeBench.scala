package org.http4s
package bench
package circe

import java.util.concurrent.TimeUnit

import io.circe.Json
import org.http4s.circe._
import org.openjdk.jmh.annotations._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
class EntityEncoderBench {
  val encoder = EntityEncoder[Json]

  @Benchmark def encodeArray(in: JsonArrayInput): Unit = {
    encoder.toEntity(in.json).run.body.run.run
  }
}

@State(Scope.Benchmark)
class JsonArrayInput {
  @Param(Array("1", "100", "10000"))
  var size: Int = _
t st
  var json: Json = _

  @Setup
  def setup(): Unit =
    json = Json.array((0 to size).map(Json.int):_*)
}