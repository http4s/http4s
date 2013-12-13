package org.http4s

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.generic.CanBuildFrom
import scala.collection.{mutable, IndexedSeqLike}
import scala.io.Codec
import scalaz.{RopeBuilder, Rope}
import java.nio.charset.Charset
import scala.reflect.ClassTag

sealed trait Chunk

class BodyChunk private (private val self: Rope[Byte]) extends Chunk
                with IndexedSeq[Byte]
                with IndexedSeqLike[Byte, BodyChunk] {

  override def iterator: Iterator[Byte] = self.iterator

  override def reverseIterator: Iterator[Byte] = self.reverseIterator

  def apply(idx: Int): Byte = self.get(idx).getOrElse(throw new IndexOutOfBoundsException(idx.toString))

  def length: Int = self.length

  override def toArray[B >: Byte : ClassTag]: Array[B] = self.toArray

  /**
   * Decodes this Chunk as a UTF-8 encoded String.
   */
  final def utf8String: String = decodeString(CharacterSet.`UTF-8`)

  /**
   * Decodes this Chunk using a charset to produce a String.
   */
  def decodeString(charset: Charset): String = new String(toArray, charset)

  /**
   * Decodes this Chunk using a charset to produce a String.
   */
  def decodeString(charset: CharacterSet): String = decodeString(charset.charset)

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder

  /**
   * Returns a read-only ByteBuffer that directly wraps this ByteString
   * if it is not fragmented.
   */
  def asByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray)

  def asInputStream: InputStream = new InputStream {
    var pos = 0

    def read(): Int = {
      val result = if (pos < length) apply(pos) else -1
      pos += 1
      result
    }
  }

  def ++(b: BodyChunk): BodyChunk = BodyChunk(self ++ b.self)

  override def foreach[U](f: (Byte) => U): Unit = iterator.foreach(f)

  /** Split the chunk into two chunks at the given index
   *
   * @param index size of left Chunk
   * @return two chunks, with the left of length size and right of the remaining length
   */
  override def splitAt(index: Int): (BodyChunk, BodyChunk) = {
    val (leftSlice, middle, rightSlice) = self.self.split1(_ <= index)
    leftSlice.isEmpty
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

case class TrailerChunk(headers: HeaderCollection = HeaderCollection.empty) extends Chunk