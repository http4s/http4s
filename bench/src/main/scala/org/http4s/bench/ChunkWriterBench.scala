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

import fs2.Chunk
import org.http4s.internal.ChunkWriter
import org.openjdk.jmh.annotations._

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Random

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class ChunkWriterBench {
  @Param(Array("100", "1000", "10000"))
  var size: Int = _

  private var singleString: String = _
  private var batchOfStings: Vector[String] = _

  private val alphaNum = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toVector

  @Setup(Level.Trial)
  def setup(): Unit = {
    singleString = Vector.fill(size)(alphaNum(Random.nextInt(alphaNum.length))).mkString
    batchOfStings = Vector.fill(size)(UUID.randomUUID().toString)
  }

  @Benchmark
  def appendSingleString: Chunk[Byte] = {
    val cw = new ChunkWriter()

    cw.append(singleString)
    cw.toChunk
  }

  @Benchmark
  def appendStringBatch: Chunk[Byte] = {
    val cw = new ChunkWriter()

    batchOfStings.foreach(cw.append)
    cw.toChunk
  }
}
