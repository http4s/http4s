package org.http4s.util

import cats._
import fs2._
import java.nio.ByteBuffer

trait ChunkInstances {

  //Maybe this belongs in fs2?
  implicit def http4sMonoidForSegment[A]: Monoid[Segment[A, Unit]] =
    new Monoid[Segment[A, Unit]] {
      def empty: Segment[A, Unit] = Segment.empty[A]

      def combine(x: Segment[A, Unit], y: Segment[A, Unit]): Segment[A, Unit] =
        x ++ y
    }

}

class ByteChunkOps(val self: Chunk[Byte]) extends AnyVal {
  def toByteBuffer: ByteBuffer = {
    val byteChunk = self.toBytes //Avoids copying
    ByteBuffer.wrap(byteChunk.values, byteChunk.offset, byteChunk.length).asReadOnlyBuffer
  }
}

trait ByteChunkSyntax {
  implicit def toByteChunkOps(self: Chunk[Byte]): ByteChunkOps =
    new ByteChunkOps(self)
}

object chunk extends AnyRef with ChunkInstances with ByteChunkSyntax
