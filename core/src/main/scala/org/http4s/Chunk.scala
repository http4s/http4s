package org.http4s

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.collection.generic.CanBuildFrom
import scala.collection.{mutable, IndexedSeqLike}
import scala.reflect.ClassTag
import scala.annotation.tailrec
import scala.io.Codec

import scalaz.{ImmutableArray, RopeBuilder, Rope}


sealed trait Chunk extends IndexedSeq[Byte] {
  def decodeString(charset: Charset): String = new String(toArray, charset)

  def decodeString(charset: CharacterSet): String = decodeString(charset.charset)

  /**
   * Returns a ByteBuffer that wraps an array copy of this BodyChunk
   */
  def asByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray)
}

class BodyChunk private (private val self: Rope[Byte]) extends Chunk with IndexedSeqLike[Byte, BodyChunk] {

  override def iterator: Iterator[Byte] = self.iterator

  override def reverseIterator: Iterator[Byte] = self.reverseIterator

  override def apply(idx: Int): Byte = self.get(idx).getOrElse(throw new IndexOutOfBoundsException(idx.toString))

  def length: Int = self.length

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit = {

    val end = if (start + len > xs.length) xs.length else start + len

    @tailrec
    def go(s: Stream[ImmutableArray[Byte]], pos: Int): Unit = if (!s.isEmpty) {
      val i = s.head
      if (i.length > end - pos) {  // To long for Array
        i.copyToArray(xs, pos, end - pos)
      }
      else {
        i.copyToArray(xs, pos, i.length)
        go(s.tail, pos + i.length)
      }
    }

    if (start < xs.length) go(self.chunks, start)
  }

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder

  def asInputStream: InputStream = new InputStream {
    private val it = iterator
    def read(): Int = if (it.hasNext) it.next() else -1
  }

  def ++(b: BodyChunk): BodyChunk = BodyChunk(self ++ b.self)

  /** Split the chunk into two chunks at the given index
   *
   * @param index size of left Chunk
   * @return two chunks, with the left of length size and right of the remaining length
   */
  override def splitAt(index: Int): (BodyChunk, BodyChunk) = {
    val (leftSlice, middle, rightSlice) = self.self.split1(_ <= index)
    val left = leftSlice :+ middle.slice(0, index - leftSlice.measure)
    val right = middle.slice(index + 1, middle.length) +: rightSlice
    
    (BodyChunk(Rope(left)), BodyChunk(Rope(right)))
  }

  override def toString(): String = s"BodyChunk(${length} bytes)"
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

  def apply(rope: Rope[Byte]): BodyChunk = new BodyChunk(rope)

  def apply(bytes: Array[Byte]): BodyChunk = BodyChunk(Rope.fromArray(bytes))

  def apply(bytes: Byte*): BodyChunk = BodyChunk(bytes.toArray)

  def apply(bytes: ByteBuffer): BodyChunk = {
    val pos = bytes.position()
    val rem = bytes.remaining()
    val n = new Array[Byte](rem)
    System.arraycopy(bytes.array(), pos, n, 0, rem)
    BodyChunk(n)
  }

  def apply(string: String): BodyChunk = apply(string, Codec.UTF8.charSet)

  def apply(string: String, charset: Charset): BodyChunk = BodyChunk(Rope.fromArray(string.getBytes(charset)))

  def fromArray(array: Array[Byte], offset: Int, length: Int): BodyChunk = BodyChunk(array.slice(offset, length))

  val empty: BodyChunk = BodyChunk(Rope.empty[Byte])

  private def newBuilder: Builder = (new RopeBuilder[Byte]).mapResult(BodyChunk.apply _)

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }
}

case class TrailerChunk(headers: HeaderCollection = HeaderCollection.empty) extends Chunk {

  def ++(chunk: TrailerChunk): TrailerChunk = {
    TrailerChunk(chunk.headers.foldLeft(headers)((other, h) => other.put(h)))
  }

  override def iterator: Iterator[Byte] = Iterator.empty

  override def toArray[B >: Byte : ClassTag]: Array[B] = Array.empty[B]

  override def apply(idx: Int): Byte = throw new IndexOutOfBoundsException("Trailer chunk doesn't contain bytes.")

  def length: Int = 0
}
