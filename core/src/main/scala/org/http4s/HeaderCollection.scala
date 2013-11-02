package org.http4s

import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom

final class HeaderCollection private (headers: List[HttpHeader])
  extends immutable.Seq[HttpHeader]
  with collection.SeqLike[HttpHeader, HeaderCollection]
{
  override protected[this] def newBuilder: mutable.Builder[HttpHeader, HeaderCollection] = HeaderCollection.newBuilder

  def length: Int = headers.length

  def apply(idx: Int): HttpHeader = headers(idx)

  def iterator: Iterator[HttpHeader] = headers.iterator

  def apply[T <: HttpHeader](key: HttpHeaderKey[T]) = get(key).get

  def get[T <: HttpHeader](key: HttpHeaderKey[T]): Option[T] = key from this

  def getAll[T <: HttpHeader](key: HttpHeaderKey[T]): Seq[T] = key findIn this

  def put(header: HttpHeader): HeaderCollection = {
    new HeaderCollection(header :: headers.filterNot(_.lowercaseName == header.lowercaseName))
  }
}

object HeaderCollection {
  val empty = apply()

  def apply(headers: HttpHeader*): HeaderCollection =  new HeaderCollection(headers.toList)

  implicit def canBuildFrom: CanBuildFrom[Traversable[HttpHeader], HttpHeader, HeaderCollection] =
    new CanBuildFrom[TraversableOnce[HttpHeader], HttpHeader, HeaderCollection] {
      def apply(from: TraversableOnce[HttpHeader]): mutable.Builder[HttpHeader, HeaderCollection] = newBuilder

      def apply(): mutable.Builder[HttpHeader, HeaderCollection] = newBuilder
    }

  private def newBuilder: mutable.Builder[HttpHeader, HeaderCollection] =
    mutable.ListBuffer.newBuilder[HttpHeader] mapResult (b => new HeaderCollection(b.result()))
}
