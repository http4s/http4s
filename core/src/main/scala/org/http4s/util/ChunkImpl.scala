package org.http4s
package util

import scala.annotation.tailrec

/**
 * Created by brycea on 4/3/14.
 */

private[http4s] case class ChunkLeafImpl(arr: Array[Byte], strt: Int, val length: Int) extends BodyChunk {

  override def apply(idx: Int): Byte = arr(idx + strt)

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit = {
    if (start < 0 || len < 0)
      throw new IndexOutOfBoundsException(s"Invalid bounds for copyToArray: Start: $start, Length: $len")

    val l = if (xs.length - start < len) xs.length - start else len
    System.arraycopy(arr, strt, xs, start, math.min(length, l))
  }

  override def splitAt(index: Int): (BodyChunk, BodyChunk) = if (index > 0) {
    val left = ChunkLeafImpl(arr, strt, math.min(index, length))
    val right = if (index < length) ChunkLeafImpl(arr, strt + index, length - index)
                else BodyChunk.empty

    (left, right)
  } else (BodyChunk.empty, this)
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

  // TODO: it would be better to implement take(n) and drop(n)
//  override def splitAt(index: Int): (BodyChunk, BodyChunk) = {
//    if (index < 1) (BodyChunk.empty, this)
//    else if (index < left.length) {
//      val (ll, lr) = left.splitAt(index)
//      (ll, lr ++ right)
//    }
//    else if (index == left.length) (left, right)
//    else {
//      val (rl, rr) = right.splitAt(index - left.length)
//      (left ++ rl, rr)
//    }
//  }
}