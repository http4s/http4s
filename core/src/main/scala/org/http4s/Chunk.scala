package org.http4s

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.collection.generic.CanBuildFrom
import scala.collection.{IndexedSeqOptimized, mutable, IndexedSeqLike}
import scala.reflect.ClassTag
import scala.io.Codec
import scala.annotation.tailrec
import scalaz.Semigroup

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

trait ChunkInstances {
  implicit val ChunkSemigroup: Semigroup[Chunk] = Semigroup.instance {
    case (a: BodyChunk, b: BodyChunk) => a ++ b
    case (a: BodyChunk, _) => a
    case (_, b: BodyChunk) => b
    case (_, _) => BodyChunk.empty
  }
}

object Chunk extends ChunkInstances

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
  final def append(b: BodyChunk): BodyChunk = BodyChunk.concat(this, b)

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
  def unsafe(arr: Array[Byte], start: Int, length: Int) = ChunkLeafImpl(arr, start, length)

  /** Empty BodyChunk */
  val empty: BodyChunk = BodyChunk()

  private def newBuilder: Builder = mutable.ArrayBuilder.make[Byte].mapResult{ arr => unsafe(arr, 0, arr.length) }

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }

  def concat(left: BodyChunk, right: BodyChunk): BodyChunk = (left, right) match {
    case (l: MultiChunkImpl, r: MultiChunkImpl) => MultiChunkImpl(l.chunks ++ r.chunks, l.length + r.length)
    case (l: MultiChunkImpl, r: BodyChunk)      => MultiChunkImpl(l.chunks :+ r, l.length + r.length)
    case (l: BodyChunk, r: MultiChunkImpl)      => MultiChunkImpl(l +: r.chunks, l.length + r.length)
    case (l: BodyChunk, r: BodyChunk)           => MultiChunkImpl(Vector.empty :+ l :+ r, l.length + r.length)
  }
}

private[http4s] case class ChunkLeafImpl(arr: Array[Byte], first: Int, val length: Int)
                              extends BodyChunk with IndexedSeqOptimized[Byte, BodyChunk] {

  if(length < 0 || arr.length - first < length)
    throw new IndexOutOfBoundsException("Invalid dimensions for new ChunkLeafImpl: " +
          s"$length, $first. Max length: ${arr.length - first }")

  override def apply(idx: Int): Byte = arr(idx + first)

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit = {
    if (start < 0 || len < 0)
      throw new IndexOutOfBoundsException(s"Invalid bounds for copyToArray: Start: $start, Length: $len")

    //val l = if (xs.length - start < len) xs.length - start else len
    val l = math.min(length, math.min(xs.length - start, len))
    System.arraycopy(arr, first, xs, start, l)
  }

  override def asByteBuffer: ByteBuffer = ByteBuffer.wrap(arr, first, length).asReadOnlyBuffer()

  // slice serves as the basis for many of the IndexSeqOptimized operations
  override def slice(from: Int, until: Int): BodyChunk = {
    if (from >= until) BodyChunk.empty
    else if (until == length && from == 0) this
    else {
      val lo = math.max(0, from)
      val hi = math.min(math.max(0, until), length)
      ChunkLeafImpl(arr, first + lo, hi - lo)
    }
  }
}

private[http4s] case class MultiChunkImpl(chunks: Vector[BodyChunk], val length: Int) extends BodyChunk {

  override def iterator: Iterator[Byte] = chunks.iterator.flatMap(_.iterator)

  override def reverseIterator: Iterator[Byte] = chunks.reverseIterator.flatMap(_.reverseIterator)

  override def apply(idx: Int): Byte = {
    @tailrec
    def go(idx: Int, vecpos: Int): Byte = {
      val c = chunks(vecpos)
      if (c.length > idx) c.apply(idx)
      else go(idx - c.length, vecpos + 1)
    }
    go(idx, 0)
  }

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit = {
    @tailrec
    def go(pos: Int, len: Int, vecpos: Int) {
      val c = chunks(vecpos)
      val maxwrite = math.min(c.length, len)
      c.copyToArray(xs, pos, maxwrite)
      if (len - maxwrite > 0 && vecpos + 1 < chunks.length)
        go(pos + maxwrite, len - maxwrite, vecpos + 1)

    }
    go(start, math.min(xs.length - start, len), 0)
  }

  override def take(n: Int): BodyChunk = {
    if (n >= length) this
    else if (n <= 0) BodyChunk.empty
    else {
      @tailrec
      def go(idx: Int, vecpos: Int): BodyChunk = {
        val v = chunks(vecpos)
        if (idx > v.length) go(idx - v.length, vecpos + 1)
        else if (idx == v.length) MultiChunkImpl(chunks.take(vecpos + 1), n)
        else MultiChunkImpl(chunks.take(vecpos):+ v.take(idx), n)
      }
      go(n, 0)
    }
  }

  override def drop(n: Int): BodyChunk = {
    if (n >= length) BodyChunk.empty
    else if (n <= 0) this
    else {
      @tailrec
      def go(idx: Int, vecpos: Int): BodyChunk = {
        val v = chunks(vecpos)
        if (idx > v.length) go(idx - v.length, vecpos + 1)
        else if (idx == v.length) MultiChunkImpl(chunks.drop(vecpos + 1), length - n)
        else MultiChunkImpl(v.drop(idx) +: chunks.drop(vecpos + 1), length - n)
      }
      go(n, 0)
    }
  }

  override def splitAt(n: Int): (BodyChunk, BodyChunk) = (take(n), drop(n))
}

/** Representation of HTTP trailers
  *
  * If the HTTP stream has a TrailerChunk, it must come at the end of the stream. Other uses may
  * case undefined behavior.
  *
  * @param headers [[Headers]] of trailers
  */
case class TrailerChunk(headers: Headers = Headers.empty) extends Chunk {

  def ++(chunk: TrailerChunk): TrailerChunk = {
    TrailerChunk(chunk.headers.foldLeft(headers)((headers, h) => headers.put(h)))
  }

  override def iterator: Iterator[Byte] = Iterator.empty

  override def toArray[B >: Byte : ClassTag]: Array[B] = Array.empty[B]

  override def apply(idx: Int): Byte = throw new IndexOutOfBoundsException("Trailer chunk doesn't contain bytes.")

  def length: Int = 0
}
