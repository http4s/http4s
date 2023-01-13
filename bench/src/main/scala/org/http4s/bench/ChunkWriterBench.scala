package org.http4s.bench

import fs2.Chunk
import org.http4s.internal.ChunkWriter
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit
import scala.util.Random

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class ChunkWriterBench {
  @Param(Array("100", "1000", "10000"))
  var size: Int = _

  private var singleString: String = _

  private val alphaNum = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toVector

  @Setup(Level.Trial)
  def setup(): Unit =
    singleString = Vector.fill(size)(alphaNum(Random.nextInt(alphaNum.length))).mkString

  @Benchmark
  def appendSingleString: Chunk[Byte] = {
    val cw = new ChunkWriter()

    cw.append(singleString)
    cw.toChunk
  }
}
