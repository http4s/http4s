package org.http4s
package blaze

import scala.reflect.ClassTag

import fs2.Chunk

import java.nio.ByteBuffer

final class ByteBufferChunk private[blaze] (byteBuffer: ByteBuffer, val size: Int) extends Chunk[Byte] {
  def apply(i: Int): Byte = byteBuffer.get(i)
  
  def copyToArray[B >: Byte](xs: Array[B], start: Int = 0): Unit = {
    val _ = byteBuffer.get(xs.asInstanceOf[Array[Byte]], start, Math.min(Math.max(byteBuffer.remaining() - start, 0), xs.length))
  }
  
  def drop(n: Int): Chunk[Byte] = {
    val slice = byteBuffer.slice()
    for (x <- 0 until n) {
      slice.get()
    }
    new ByteBufferChunk(slice, slice.remaining())
  }
  
  def take(n: Int): Chunk[Byte] = {
    val slice = byteBuffer.slice()
    new ByteBufferChunk(slice, Math.min(slice.remaining(), n))
  }

  def filter(f: Byte => Boolean): Chunk[Byte] = {
    ???
  }

  def conform[B: ClassTag]: Option[Chunk[B]] = {
    ???
  }

  def foldLeft[B](z: B)(f: (B, Byte) => B): B = {
    var s = z
    val slice = byteBuffer.slice()
    while (slice.hasRemaining()) {
      s = f(s, slice.get())
    }
    println(s)
    s
  }

  def foldRight[B](z: B)(f: (Byte, B) => B): B = ???

}

object ByteBufferChunk {
  def apply(byteBuffer: ByteBuffer): Chunk[Byte] =
    new ByteBufferChunk(byteBuffer, byteBuffer.remaining())
}
