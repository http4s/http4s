package org.http4s.syntax

import fs2.Chunk
import fs2.interop.scodec.ByteVectorChunk
import java.nio.ByteBuffer

trait ByteChunkSyntax {
  implicit def http4sByteChunkSyntax(self: Chunk[Byte]): ByteChunkOps =
    new ByteChunkOps(self)
}

final class ByteChunkOps(val self: Chunk[Byte]) extends AnyVal {
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
