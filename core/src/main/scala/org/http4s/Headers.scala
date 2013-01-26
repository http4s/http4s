package org.http4s

import scala.collection.{mutable, immutable}
import scala.collection.generic.CanBuildFrom

class Headers private(headers: Seq[Header])
  extends immutable.Seq[Header]
  with collection.SeqLike[Header, Headers]
{
  override protected[this] def newBuilder: mutable.Builder[Header, Headers] = Headers.newBuilder

  def length: Int = headers.length

  def apply(idx: Int): Header = headers(idx)

  def iterator: Iterator[Header] = headers.iterator

  def apply(name: String): String = get(name).get

  def get(name: String): Option[String] = find(_.name == name).map(_.value)

  def getAll(name: String): Seq[String] = filter(_.name == name).map(_.value)
}

object Headers {
  val Empty = apply()

  def apply(headers: Header*): Headers = new Headers(headers)

  implicit def canBuildFrom: CanBuildFrom[Headers, Header, Headers] =
    new CanBuildFrom[Headers, Header, Headers] {
      def apply(from: Headers): mutable.Builder[Header, Headers] = newBuilder
      def apply(): mutable.Builder[Header, Headers] = newBuilder
    }

  private def newBuilder: mutable.Builder[Header, Headers] =
    new mutable.ListBuffer[Header] mapResult(xs => new Headers(xs))
}

case class Header(name: String, value: String)
