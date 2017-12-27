package org.http4s.internal

import fs2.Chunk
import java.nio.{ByteBuffer => JByteBuffer}
import scala.collection.immutable.VectorBuilder
import scala.reflect.ClassTag

private[http4s] object chunk {
  implicit class http4sInternalChunkObjectSyntax(val self: Chunk.type) extends AnyVal {
    /** Creates a chunk backed by an byte buffer, bounded by the current position and limit */
    def byteBuffer(buf: JByteBuffer): Chunk[Byte] = ByteBuffer(buf)
  }

  final case class ByteBuffer private (buf: JByteBuffer, offset: Int, size: Int) extends Chunk[Byte] {
    def apply(i: Int): Byte = buf.get(i + offset)
    protected def splitAtChunk_(n: Int): (Chunk[Byte], Chunk[Byte]) = {
      val first = buf.asReadOnlyBuffer
      first.limit(n + offset)
      val second = buf.asReadOnlyBuffer
      second.position(n + offset)
      (ByteBuffer(first), ByteBuffer(second))
    }
    override def map[O2](f: Byte => O2): Chunk[O2] = {
      val b = new VectorBuilder[O2]
      b.sizeHint(size)
      for (i <- offset until size + offset) {
        b += f(buf.get(i))
      }
      Chunk.indexedSeq(b.result)
    }
    override def toArray[O2 >: Byte: ClassTag]: Array[O2] = {
      val bs = new Array[Byte](size)
      val b = buf.duplicate
      b.position(offset)
      b.get(bs, 0, size)
      bs.asInstanceOf[Array[O2]]
    }
  }
  object ByteBuffer { def apply(buf: JByteBuffer): ByteBuffer = ByteBuffer(buf, buf.position, buf.remaining) }

  implicit class http4sInternalChunkSyntax[O](val self: Chunk[O]) extends AnyVal {
    def toByteBuffer[B >: O](implicit ev: B =:= Byte): JByteBuffer = {
      val _ = ev // Convince scalac that ev is used
      self match {
        case c: Chunk.Bytes =>
          JByteBuffer.wrap(c.values, c.offset, c.length)
        case c: ByteBuffer =>
          val b = c.buf.asReadOnlyBuffer
          if (c.offset == 0 && b.position == 0 && c.size == b.limit) b
          else {
            b.position(c.offset.toInt)
            b.limit(c.offset.toInt + c.size)
            b
          }
        case other =>
          JByteBuffer.wrap(other.asInstanceOf[Chunk[Byte]].toArray, 0, other.size)
      }
    }
  }
}
