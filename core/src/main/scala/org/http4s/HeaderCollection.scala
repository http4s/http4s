package org.http4s

import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom

final class HeaderCollection private (headers: List[Header])
  extends AnyRef with immutable.Seq[Header]
  with collection.SeqLike[Header, HeaderCollection]
{
  override protected[this] def newBuilder: mutable.Builder[Header, HeaderCollection] = HeaderCollection.newBuilder

  override def tail = new HeaderCollection(headers.tail)

  override def head = headers.head

  override def foreach[B](f: Header => B) = headers.foreach(f)

  def +: (header: Header) = new HeaderCollection(header::headers)

  override def drop(n: Int) = new HeaderCollection(headers.drop(n))

  def length: Int = headers.length

  def apply(idx: Int): Header = headers(idx)

  def iterator: Iterator[Header] = headers.iterator

  def apply[T <: Header](key: HeaderKey[T]) = get(key).get

  def get[T <: Header](key: HeaderKey[T]): Option[T] = key from this

  def getAll[T <: Header](key: HeaderKey[T]): Seq[T] = key findIn this

  def put(header: Header): HeaderCollection =
    new HeaderCollection(header :: headers.filterNot(_.getClass == header.getClass))
}

object HeaderCollection {
  val empty = apply()

  def apply(headers: Header*): HeaderCollection = HeaderCollection(headers.toList)

  def apply(headers: List[Header]): HeaderCollection = new HeaderCollection(headers)

  implicit def canBuildFrom: CanBuildFrom[Traversable[Header], Header, HeaderCollection] =
    new CanBuildFrom[TraversableOnce[Header], Header, HeaderCollection] {
      def apply(from: TraversableOnce[Header]): mutable.Builder[Header, HeaderCollection] = newBuilder

      def apply(): mutable.Builder[Header, HeaderCollection] = newBuilder
    }

  private def newBuilder: mutable.Builder[Header, HeaderCollection] =
    new mutable.ListBuffer[Header] mapResult (b => new HeaderCollection(b))
}
