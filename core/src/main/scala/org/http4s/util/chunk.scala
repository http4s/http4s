package org.http4s.util

import cats._
import fs2._
import java.nio.ByteBuffer

trait ChunkInstances extends ChunkInstances0 {

  /** Specialization for byte chunks, which is mostly what we want. */
  implicit val ByteChunkMonoid: Monoid[Chunk[Byte]] =
    new Monoid[Chunk[Byte]] {
      def combine(chunk1: Chunk[Byte], chunk2: Chunk[Byte]): Chunk[Byte] =
        Chunk.bytes(chunk1.toBytes.values ++ chunk2.toBytes.values)

      val empty: Chunk[Byte] =
        Chunk.empty
    }
}

trait ChunkInstances0 {
  implicit def ChunkMonoid[A]: Monoid[Chunk[A]] =
    new Monoid[Chunk[A]] {
      def combine(chunk1: Chunk[A], chunk2: Chunk[A]): Chunk[A] =
        (chunk1 ++ chunk2).toChunk

      def empty: Chunk[A] =
        Chunk.empty
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
