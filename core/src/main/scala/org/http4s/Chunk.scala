package org.http4s

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.collection.generic.CanBuildFrom
import scala.collection.{IndexedSeqOptimized, mutable, IndexedSeqLike}
import scala.reflect.ClassTag
import scala.io.Codec


/** Chunks are the currency of the HTTP body
  *
  * Chunks come on two forms: [[BodyChunk]]s and [[TrailerChunk]]s.
  */
sealed trait Chunk extends IndexedSeq[Byte] {

  /** Decode this Chunk into a String using the provided Charset */
  def decodeString(charset: Charset): String = new String(toArray, charset)

  /** Decode this Chunk into a String using the provided [[CharacterSet]] */
  def decodeString(charset: CharacterSet): String = decodeString(charset.charset)

  /** Returns a read-only ByteBuffer representation of this BodyChunk
    */
  def asByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray).asReadOnlyBuffer()
}

/** Provides the container for Bytes on the [[org.http4s.Request]] and [[org.http4s.Response]] */
trait BodyChunk extends Chunk with IndexedSeqLike[Byte, BodyChunk] {

  def length: Int

  override def iterator: Iterator[Byte]

  override def reverseIterator: Iterator[Byte]

  override def apply(idx: Int): Byte

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder

  def asInputStream: InputStream = new ByteArrayInputStream(toArray)

  /** Append the BodyChunk to the end of this BodyChunk */
  final def append(b: BodyChunk): BodyChunk = util.MultiChunkImpl.concat(this, b)

  /** Append the BodyChunk to the end of this BodyChunk
   */
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

  /** Create a new BodyChunk with a copy of the provided Array[Byte]
    *
    * @param bytes array of bytes for the BodyChunk
    */
  def apply(bytes: Array[Byte]): BodyChunk = apply(bytes, 0, bytes.length)

  /** Create a new BodyChunk with a copy of the provided Array[Byte]
    *
    * @param bytes array of bytes to form the BodyChunk
    * @param start starting position in the Array
    * @param length number of Bytes desired
    */
  def apply(bytes: Array[Byte], start: Int, length: Int) = {
    if (start > bytes.length || length > bytes.length - start)
      throw new IndexOutOfBoundsException("Invalid bounds for BodyChunk construction: " +
                                          s"Array(${bytes.length}), Start: $start, Length: $length")

    val arr = new Array[Byte](length)
    System.arraycopy(bytes, start, arr, 0, length)
    unsafe(arr, 0, length)
  }

  /** Create a new BodyChunk from the provided Bytes */
  def apply(bytes: Byte*): BodyChunk = {
    val arr = bytes.toArray
    unsafe(arr, 0, arr.length)
  }

  /** Create a new BodyChunk from a ByteBuffer
    *
    * @param bytes ByteBuffer containing the Bytes to copy into a BodyChunk
    *
    *             ''The ByteBuffer position will be set to the end of the buffer''
    */
  def apply(bytes: ByteBuffer): BodyChunk = {
    val n = new Array[Byte](bytes.remaining())
    bytes.get(n)
    BodyChunk(n)
  }

  def apply(string: String): BodyChunk = apply(string, Codec.UTF8.charSet)

  def apply(string: String, charset: Charset): BodyChunk = {
    val bytes = string.getBytes(charset)
    unsafe(bytes, 0, bytes.length)
  }

  /** Construct and unsafe BodyChunk from the array
    * The array is not copied, and therefor any changes will result in changes to the  resulting BodyChunk!
    * If this is not the desired behavior, use BodyChunk.apply to safely copy the array first
    */
  def unsafe(arr: Array[Byte], start: Int, length: Int) = util.ChunkLeafImpl(arr, start, length)

  /** Empty BodyChunk */
  val empty: BodyChunk = BodyChunk()

  private def newBuilder: Builder = mutable.ArrayBuilder.make[Byte].mapResult{ arr => unsafe(arr, 0, arr.length) }

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }
}

/** Representation of HTTP trailers
  *
  * If the HTTP stream has a TrailerChunk, it must come at the end of the stream. Other uses may
  * case undefined behavior.
  *
  * @param headers [[HeaderCollection]] of trailers
  */
case class TrailerChunk(headers: HeaderCollection = HeaderCollection.empty) extends Chunk {

  def ++(chunk: TrailerChunk): TrailerChunk = {
    TrailerChunk(chunk.headers.foldLeft(headers)((headers, h) => headers.put(h)))
  }

  override def iterator: Iterator[Byte] = Iterator.empty

  override def toArray[B >: Byte : ClassTag]: Array[B] = Array.empty[B]

  override def apply(idx: Int): Byte = throw new IndexOutOfBoundsException("Trailer chunk doesn't contain bytes.")

  def length: Int = 0
}
