package org.http4s
package util

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

private[http4s] case class ChunkNodeImpl(left: BodyChunk, right: BodyChunk) extends BodyChunk {

  override def iterator: Iterator[Byte] = left.iterator ++ right.iterator

  override def reverseIterator: Iterator[Byte] = right.reverseIterator ++ left.reverseIterator

  override def apply(idx: Int): Byte = if (left.length > idx) left(idx) else (right(idx - left.length))

  val length = left.length + right.length

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit = {
    left.copyToArray(xs, start, len)
    val llen = left.length
    val remaining = len - llen
    if (remaining > 0 && xs.length - start - left.length > 0)
      right.copyToArray(xs, start + left.length, remaining)
  }

  override def splitAt(index: Int): (BodyChunk, BodyChunk) = {
    if (index < 1) (BodyChunk.empty, this)
    else if (index < left.length) {
      val (ll, lr) = left.splitAt(index)
      (ll, lr ++ right)
    }
    else if (index == left.length) (left, right)
    else {
      val (rl, rr) = right.splitAt(index - left.length)
      (left ++ rl, rr)
    }
  }
}