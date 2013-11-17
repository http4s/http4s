package org.http4s

import akka.util.{ByteStringBuilder, ByteIterator, ByteString}
import java.nio.ByteBuffer
import scala.collection.generic.CanBuildFrom
import scala.collection.{mutable, IndexedSeqOptimized}

sealed trait Chunk

case class BodyChunk(bytes: ByteString) extends Chunk
  with IndexedSeq[Byte] with IndexedSeqOptimized[Byte, BodyChunk]
{
  override def apply(idx: Int): Byte = bytes(idx)

  override def toArray[B >: Byte](implicit evidence$1: scala.reflect.ClassTag[B]): Array[B] = bytes.toArray

  def length: Int = bytes.length

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder

  override def iterator: ByteIterator = bytes.iterator

  /**
   * Returns a read-only ByteBuffer that directly wraps this ByteString
   * if it is not fragmented.
   */
  def asByteBuffer: ByteBuffer = bytes.asByteBuffer

  /**
   * Creates a new ByteBuffer with a copy of all bytes contained in this
   * ByteString.
   */
  def toByteBuffer: ByteBuffer = bytes.toByteBuffer

  /**
   * Decodes this ByteString as a UTF-8 encoded String.
   */
  final def utf8String: String = decodeString(CharacterSet.`UTF-8`)

  /**
   * Decodes this ByteString using a charset to produce a String.
   */
  def decodeString(charset: CharacterSet): String = bytes.decodeString(charset.value)
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

  def apply(bytes: Array[Byte]): BodyChunk = BodyChunk(ByteString(bytes))

  def apply(bytes: Byte*): BodyChunk = BodyChunk(ByteString(bytes: _*))

  def apply[T](bytes: T*)(implicit num: Integral[T]): BodyChunk = BodyChunk(ByteString(bytes: _*)(num))

  def apply(bytes: ByteBuffer): BodyChunk = BodyChunk(ByteString(bytes))

  def apply(string: String): BodyChunk = apply(string, CharacterSet.`UTF-8`)

  def apply(string: String, charset: CharacterSet): BodyChunk = BodyChunk(ByteString(string, charset.value))

  def fromArray(array: Array[Byte], offset: Int, length: Int): BodyChunk =
    BodyChunk(ByteString.fromArray(array, offset, length))

  val Empty: BodyChunk = BodyChunk(ByteString.empty)

  private def newBuilder: Builder = (new ByteStringBuilder).mapResult(BodyChunk(_))

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }
}

case class TrailerChunk(headers: HeaderCollection = HeaderCollection.empty) extends Chunk {
  final def bytes: ByteString = ByteString.empty
}