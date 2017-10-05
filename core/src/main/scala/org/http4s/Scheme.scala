package org.http4s

import cats.{Order, Show}
import org.http4s.util.{hashLower, Renderer, Writer}

final class Scheme(val value: String) extends Comparable[Scheme] {
  override def equals(o: Any) = o match {
    case that: Scheme => this.value.equalsIgnoreCase(that.value)
    case _ => false
  }

  private[this] var hash = 0
  override def hashCode(): Int = {
    if (hash == 0) {
      hash = hashLower(value)
    }
    hash
  }

  override def toString = s"Scheme($value)"

  override def compareTo(other: Scheme): Int =
    value.compareToIgnoreCase(other.value)
}

object Scheme {
  def apply(s: String): Scheme =
    new Scheme(s)

  def fromString(s: String): ParseResult[Scheme] =
    Right(new Scheme(s))

  val http = Scheme("http")
  val https = Scheme("https")

  implicit val http4sInstancesForScheme: Show[Scheme] with Renderer[Scheme] with Order[Scheme] =
    new Show[Scheme] with Renderer[Scheme] with Order[Scheme] {
      def show(s: Scheme): String = s.toString

      def render(writer: Writer, scheme: Scheme): writer.type =
        writer << scheme.value

      def compare(x: Scheme, y: Scheme) =
        x compareTo y
    }
}
