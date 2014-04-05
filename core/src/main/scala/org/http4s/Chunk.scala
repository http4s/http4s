package org.http4s

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.collection.generic.CanBuildFrom
import scala.collection.{mutable, IndexedSeqLike}
import scala.reflect.ClassTag
import scala.io.Codec
import scalaz.Semigroup

sealed trait Chunk extends IndexedSeq[Byte] {
  def decodeString(charset: Charset): String = new String(toArray, charset)

  def decodeString(charset: CharacterSet): String = decodeString(charset.charset)

  /** Returns a ByteBuffer that wraps an array copy of this BodyChunk
   */
  def asByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray)
}

trait ChunkInstances {
  implicit val ChunkSemigroup: Semigroup[Chunk] = Semigroup.instance {
    case (a: BodyChunk, b: BodyChunk) => a ++ b
    case (a: BodyChunk, _) => a
    case (_, b: BodyChunk) => b
    case (_, _) => BodyChunk.empty
  }
}

object Chunk extends ChunkInstances

trait BodyChunk extends Chunk with IndexedSeqLike[Byte, BodyChunk] {

  def length: Int

  override def iterator: Iterator[Byte]

  override def reverseIterator: Iterator[Byte]

  override def apply(idx: Int): Byte

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder

  def asInputStream: InputStream = new ByteArrayInputStream(toArray)

  final def append(b: BodyChunk): BodyChunk = util.MultiChunkImpl.concat(this, b)

  final def ++ (b: BodyChunk): BodyChunk = append(b)

  /** Split the chunk into two chunks at the given index
   *
   * @param index size of left Chunk
   * @return two chunks, with the left of length size and right of the remaining length
   */
  override def splitAt(index: Int): (BodyChunk, BodyChunk)

  override def toString(): String = s"BodyChunk(${length} bytes)"
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

  def apply(bytes: Array[Byte]): BodyChunk = apply(bytes, 0, bytes.length)

  def apply(bytes: Array[Byte], start: Int, length: Int) = {
    if (start > bytes.length || length > bytes.length - start)
      throw new IndexOutOfBoundsException("Invalid bounds for BodyChunk construction: " +
                                          s"Array(${bytes.length}), Start: $start, Length: $length")

    unsafe(bytes.clone(), start, length)
  }

  def apply(bytes: Byte*): BodyChunk = BodyChunk(bytes.toArray)

  def apply(bytes: ByteBuffer): BodyChunk = {
    val n = new Array[Byte](bytes.remaining())
    bytes.get(n)
    BodyChunk(n)
  }

  def apply(string: String): BodyChunk = apply(string, Codec.UTF8.charSet)

  def apply(string: String, charset: Charset): BodyChunk = BodyChunk(string.getBytes(charset))

  def fromArray(array: Array[Byte], offset: Int, length: Int): BodyChunk = BodyChunk(array.slice(offset, length))

  /** Construct and unsafe BodyChunk from the array
    * The array is not copied, and therefor any changes will result in changes to the  resulting BodyChunk!
    * If this is not the desired behavior, use BodyChunk.apply to safely copy the array first
    */
  def unsafe(arr: Array[Byte], start: Int, length: Int) = util.ChunkLeafImpl(arr, start, length)

  val empty: BodyChunk = BodyChunk()

  private def newBuilder: Builder = (mutable.ArrayBuilder.make[Byte]).mapResult(BodyChunk(_))

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }
}

case class TrailerChunk(headers: HeaderCollection = HeaderCollection.empty) extends Chunk {

  def ++(chunk: TrailerChunk): TrailerChunk = {
    TrailerChunk(chunk.headers.foldLeft(headers)((headers, h) => headers.put(h)))
  }

  override def iterator: Iterator[Byte] = Iterator.empty

  override def toArray[B >: Byte : ClassTag]: Array[B] = Array.empty[B]

  override def apply(idx: Int): Byte = throw new IndexOutOfBoundsException("Trailer chunk doesn't contain bytes.")

  def length: Int = 0
}
