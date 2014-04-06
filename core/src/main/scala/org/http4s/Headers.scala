package org.http4s

import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom

final class Headers private (private val headers: List[Header])
  extends AnyRef with immutable.Seq[Header]
  with collection.SeqLike[Header, Headers]
{
  override protected[this] def newBuilder: mutable.Builder[Header, Headers] = Headers.newBuilder

  override def tail = new Headers(headers.tail)

  override def head = headers.head

  override def foreach[B](f: Header => B) = headers.foreach(f)

  def +: (header: Header) = new Headers(header::headers)

  override def drop(n: Int) = new Headers(headers.drop(n))

  def length: Int = headers.length

  def apply(idx: Int): Header = headers(idx)

  def iterator: Iterator[Header] = headers.iterator

  def get(key: ExtractableHeaderKey): Option[key.HeaderT] = key.from(this)

  def put(header: Header): Headers =
    new Headers(header :: headers.filterNot(_.getClass == header.getClass))
}

object Headers {
  val empty = apply()

  def apply(headers: Header*): Headers = Headers(headers.toList)

  def apply(headers: List[Header]): Headers = new Headers(headers)

  implicit def canBuildFrom: CanBuildFrom[Traversable[Header], Header, Headers] =
    new CanBuildFrom[TraversableOnce[Header], Header, Headers] {
      def apply(from: TraversableOnce[Header]): mutable.Builder[Header, Headers] = newBuilder

      def apply(): mutable.Builder[Header, Headers] = newBuilder
    }

  private def newBuilder: mutable.Builder[Header, Headers] =
    new mutable.ListBuffer[Header] mapResult (b => new Headers(b))
}
