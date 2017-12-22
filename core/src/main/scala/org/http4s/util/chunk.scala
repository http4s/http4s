package org.http4s.util

import fs2._
import fs2.interop.scodec.ByteVectorChunk
import java.nio.ByteBuffer

class ByteChunkOps(val self: Chunk[Byte]) extends AnyVal {
  def toByteBuffer: ByteBuffer =
    self match {
      case bvc: ByteVectorChunk =>
        bvc.toByteVector.toByteBuffer
      case bs: Chunk.Bytes =>
        bs.toByteBuffer
      case _ =>
        val byteChunk = self.toBytes //Avoids copying
        ByteBuffer.wrap(byteChunk.values, byteChunk.offset, byteChunk.length).asReadOnlyBuffer
    }
}

trait ByteChunkSyntax {
  implicit def toByteChunkOps(self: Chunk[Byte]): ByteChunkOps =
    new ByteChunkOps(self)
}

object chunk extends AnyRef with ByteChunkSyntax
