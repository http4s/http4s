package org.http4s
package util

import fs2.{Chunk, MonomorphicChunk}
import scodec.bits.ByteVector

import scala.reflect.{ClassTag, classTag}

final class ByteVectorChunk private (val toByteVector: ByteVector)
  extends MonomorphicChunk[Byte] {
  def apply(i: Int): Byte =
    toByteVector(i.toLong)

  def copyToArray[B >: Byte](xs: Array[B], start: Int): Unit =
    xs match {
      case byteArray: Array[Byte] =>
        toByteVector.copyToArray(byteArray, start)
      case _ =>
        iterator.copyToArray(xs, start)
    }

  def drop(n: Int): Chunk[Byte] =
    ByteVectorChunk(toByteVector.drop(n.toLong))

  def filter(f: Byte => Boolean): Chunk[Byte] = {
    var i = 0L
    val bound = toByteVector.size

    val values2 = new Array[Byte](size)
    var size2 = 0

    while (i < bound) {
      val b = toByteVector(i)
      if (f(b)) {
        values2(size2) = toByteVector(i)
        size2 += 1
      }

      i += 1
    }

    ByteVectorChunk(ByteVector.view(values2, 0, size2))
  }

  def foldLeft[B](z: B)(f: (B, Byte) => B): B =
    toByteVector.foldLeft(z)(f)

  def foldRight[B](z: B)(f: (Byte, B) => B): B =
    toByteVector.foldRight(z)(f)

  def size: Int =
    toByteVector.size.toInt

  def take(n: Int): Chunk[Byte] =
    ByteVectorChunk(toByteVector.take(n.toLong))

  protected val tag: ClassTag[_] =
    classTag[Byte]

  override def concatAll[B >: Byte](chunks: Seq[Chunk[B]]): Chunk[B] = {
    val conformed = chunks flatMap { _.conform[Byte] }
    if (chunks.isEmpty) {
      this
    } else if ((chunks lengthCompare conformed.size) == 0) {
      ByteVectorChunk(conformed.foldLeft(toByteVector) {
        case (bv, bvc: ByteVectorChunk) => bv ++ bvc.toByteVector
        case (bv, c) => bv ++ ByteVector.view(c.toArray)
      })
    } else {
      super.concatAll(chunks)
    }
  }
}

object ByteVectorChunk {
  def apply(bv: ByteVector): ByteVectorChunk =
    new ByteVectorChunk(bv)
}
