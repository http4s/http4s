package org.http4s.util

import cats._
import fs2._
import scodec.bits.ByteVector

trait ByteVectorSyntax {
  implicit class ChunkBytesOps(val self: Chunk[Byte]) {
    def toByteVector: ByteVector =
      ByteVector.view(self.toArray)
  }

  implicit class ByteVectorOps(val self: ByteVector) {
    def toChunk: Chunk[Byte] =
      Chunk.bytes(self.toArray)
  }
}

trait ByteVectorInstances {
  // This is defined in sodec, which we don't depend on.
  implicit val byteVectorMonoidInstance: Monoid[ByteVector] =
    new Monoid[ByteVector] {
      def combine(a1: ByteVector, a2: ByteVector): ByteVector =
        a1 ++ a2
      val empty: ByteVector =
        ByteVector.empty
    }
}

object byteVector extends AnyRef
    with ByteVectorSyntax
    with ByteVectorInstances
