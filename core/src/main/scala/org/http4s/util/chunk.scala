package org.http4s.util

import cats._
import fs2._
import java.nio.ByteBuffer

trait ChunkInstances {

  implicit def http4sMonoidForChunk[A]: Monoid[Chunk[A]] =
    new Monoid[Chunk[A]] {
      // This smells
      override def combine(x: Chunk[A], y: Chunk[A]): Chunk[A] =
        (x.toSegment ++ y.toSegment).force.toChunk

      override def empty: Chunk[A] = Chunk.empty
    }

}

class ByteChunkOps(val self: Chunk[Byte]) extends AnyVal {
  def toByteBuffer: ByteBuffer =
    ByteBuffer.wrap(self.toArray).asReadOnlyBuffer
}

trait ByteChunkSyntax {
  implicit def toByteChunkOps(self: Chunk[Byte]): ByteChunkOps =
    new ByteChunkOps(self)
}

object chunk extends AnyRef with ChunkInstances with ByteChunkSyntax
