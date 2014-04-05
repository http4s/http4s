package org.http4s
package util

import scala.annotation.tailrec
import java.nio.ByteBuffer
import scala.collection.IndexedSeqOptimized

/**
 * Created by Bryce Anderson on 4/3/14.
 */

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

private[http4s] object MultiChunkImpl {
  def concat(left: BodyChunk, right: BodyChunk): BodyChunk = (left, right) match {
    case (l: MultiChunkImpl, r: MultiChunkImpl) => MultiChunkImpl(l.chunks ++ r.chunks, l.length + r.length)
    case (l: MultiChunkImpl, r: BodyChunk)      => MultiChunkImpl(l.chunks :+ r, l.length + r.length)
    case (l: BodyChunk, r: MultiChunkImpl)      => MultiChunkImpl(l +: r.chunks, l.length + r.length)
    case (l: BodyChunk, r: BodyChunk)           => MultiChunkImpl(Vector.empty :+ l :+ r, l.length + r.length)
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