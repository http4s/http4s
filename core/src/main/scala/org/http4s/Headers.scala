package org.http4s

import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer

/** A collection of HTTP Headers */
final class Headers private (headers: List[Header])
  extends AnyRef with immutable.Seq[Header]
  with collection.SeqLike[Header, Headers]
{
  override protected def newBuilder: mutable.Builder[Header, Headers] = Headers.newBuilder

  override def tail: Headers = new Headers(headers.tail)

  override def head: Header = headers.head

  override def foreach[B](f: Header => B): Unit = headers.foreach(f)

  def +: (header: Header): Headers = new Headers(header::headers)

  override def drop(n: Int): Headers = new Headers(headers.drop(n))

  def length: Int = headers.length

  def apply(idx: Int): Header = headers(idx)

  def iterator: Iterator[Header] = headers.iterator

  def get(key: HeaderKey.Extractable): Option[key.HeaderT] = key.from(this)

  def put(head: Header, tail: Header*): Headers = {
    if (tail.isEmpty) {
      new Headers(head :: headers.filterNot(_.name == head.name))
    }
    else {
      val b = new ListBuffer[Header] += head ++= tail
      val n = b.prependToList(headers.filterNot(h => head.name == h.name || tail.exists(_.name == h.name)))
      new Headers(n)
    }
    
  }
    
}

object Headers {
  val empty = apply()

  /** Create a new Headers collection from the headers */
  def apply(headers: Header*): Headers = Headers(headers.toList)

  /** Create a new Headers collection from the headers */
  def apply(headers: List[Header]): Headers = new Headers(headers)

  implicit def canBuildFrom: CanBuildFrom[Traversable[Header], Header, Headers] =
    new CanBuildFrom[TraversableOnce[Header], Header, Headers] {
      def apply(from: TraversableOnce[Header]): mutable.Builder[Header, Headers] = newBuilder

      def apply(): mutable.Builder[Header, Headers] = newBuilder
    }

  private def newBuilder: mutable.Builder[Header, Headers] =
    new mutable.ListBuffer[Header] mapResult (b => new Headers(b))
}
