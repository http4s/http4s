package org.http4s.bench

import fs2.{Segment, Chunk}
import fs2.interop.scodec.ByteVectorChunk
import org.openjdk.jmh.annotations._
import scodec.bits.ByteVector

@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
class BytesBench {
  val bs = new Array[Byte](1024)
  scala.util.Random.nextBytes(bs)

  @Benchmark def byteVectorChunkToByteBuffer() = {
    val s = Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs)))
    s.force.foreachChunk { c =>
      c.toByteBuffer; ()
    }
  }

  @Benchmark def bytesToByteBuffer() = {
    val s = Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array))
    s.force.foreachChunk { c =>
      c.toByteBuffer; ()
    }
  }

  @Benchmark def byteVectorChunkToArray() = {
    val s = Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs))) ++
      Segment.chunk(ByteVectorChunk(ByteVector.view(bs)))
    s.force.toArray
  }

  @Benchmark def bytesToArray() = {
    val s = Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array)) ++
      Segment.chunk(Chunk.bytes(bs.array))
    s.force.toArray
  }
}
