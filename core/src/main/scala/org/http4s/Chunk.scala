package org.http4s

import java.io.InputStream
import java.nio.ByteBuffer
import scala.collection.generic.CanBuildFrom
import scala.collection.{mutable, IndexedSeqOptimized}
import scala.io.Codec
import scalaz.{RopeBuilder, Rope}
import java.nio.charset.Charset
import scala.reflect.ClassTag

sealed trait Chunk extends IndexedSeq[Byte] {

  def asInputStream: InputStream = new InputStream {
    var pos = 0

    def read(): Int = {
      val result = if (pos < length) apply(pos) else -1
      pos += 1
      result
    }
  }

  def decodeString(charset: Charset): String = new String(toArray, charset)

  def decodeString(charset: CharacterSet): String = decodeString(charset.charset)
}

class BodyChunk private (private val self: Rope[Byte]) extends Chunk with IndexedSeqOptimized[Byte, BodyChunk]
{
  override def apply(idx: Int): Byte = self.get(idx).getOrElse(throw new IndexOutOfBoundsException(idx.toString))

  def length: Int = self.length

  override def toArray[B >: Byte : ClassTag]: Array[B] = self.toArray

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder
  /*
    override def iterator: ByteIterator = bytes.iterator

    /**
     * Returns a read-only ByteBuffer that directly wraps this ByteString
     * if it is not fragmented.
     */
    def asByteBuffer: ByteBuffer = bytes.asByteBuffer

    /**
     * Decodes this ByteString as a UTF-8 encoded String.
     */
    final def utf8String: String = decodeString(CharacterSet.`UTF-8`)

    /**
     * Decodes this ByteString using a charset to produce a String.
     */
    def decodeString(charset: CharacterSet): String = bytes.decodeString(charset.value)
  */

  def ++(b: BodyChunk): BodyChunk = BodyChunk(self ++ b.self)
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

  def apply(rope: Rope[Byte]): BodyChunk = new BodyChunk(rope)

  def apply(bytes: Array[Byte]): BodyChunk = BodyChunk(Rope.fromArray(bytes))

  def apply(bytes: Byte*): BodyChunk = BodyChunk(bytes.toArray)

  def apply(bytes: ByteBuffer): BodyChunk = BodyChunk(bytes.array)

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
  override def apply(idx: Int): Byte = throw new IndexOutOfBoundsException(idx.toString)

  def length: Int = 0
}
