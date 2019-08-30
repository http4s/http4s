package org.http4s

import cats._
import cats.implicits._
import org.http4s.Query._
import org.http4s.internal.CollectionCompat
import org.http4s.internal.parboiled2.CharPredicate
import org.http4s.parser.QueryParser
import org.http4s.util.{Renderable, Writer}
import scala.collection.immutable

/** Collection representation of a query string
  *
  * It is a indexed sequence of key and maybe a value pairs which maps
  * precisely to a query string, modulo the identity of separators.
  *
  * When rendered, the resulting `String` will have the pairs separated
  * by '&' while the key is separated from the value with '='
  */
final class Query private (val pairs: Vector[KeyValue]) extends QueryOps with Renderable {
  def apply(idx: Int): KeyValue = pairs(idx)

  def length: Int = pairs.length

  def slice(from: Int, until: Int): Query = new Query(pairs.slice(from, until))

  def isEmpty: Boolean = pairs.isEmpty

  def nonEmpty: Boolean = pairs.nonEmpty

  def drop(n: Int): Query = new Query(pairs.drop(n))

  def dropRight(n: Int): Query = new Query(pairs.dropRight(n))

  def exists(f: KeyValue => Boolean): Boolean =
    pairs.exists(f)

  def filterNot(f: KeyValue => Boolean): Query =
    new Query(pairs.filterNot(f))

  def filter(f: KeyValue => Boolean): Query =
    new Query(pairs.filter(f))

  def foreach(f: KeyValue => Unit): Unit =
    pairs.foreach(f)

  def foldLeft[Z](z: Z)(f: (Z, KeyValue) => Z): Z =
    pairs.foldLeft(z)(f)

  def foldRight[Z](z: Eval[Z])(f: (KeyValue, Eval[Z]) => Eval[Z]): Eval[Z] =
    Foldable[Vector].foldRight(pairs, z)(f)

  def +:(elem: KeyValue): Query =
    new Query(elem +: pairs)

  def :+(elem: KeyValue): Query =
    new Query(pairs :+ elem)

  def ++(pairs: collection.Iterable[(String, Option[String])]): Query =
    new Query(this.pairs ++ pairs)

  def toVector: Vector[(String, Option[String])] = pairs

  def toList: List[(String, Option[String])] = toVector.toList

  /** Render the Query as a `String`.
    *
    * Pairs are separated by '&' and keys are separated from values by '='
    */
  override def render(writer: Writer): writer.type = {
    var first = true
    def encode(s: String) =
      Uri.urlEncode(s, spaceIsPlus = false, toSkip = NoEncode)
    pairs.foreach {
      case (n, None) =>
        if (!first) writer.append('&')
        else first = false
        writer.append(encode(n))

      case (n, Some(v)) =>
        if (!first) writer.append('&')
        else first = false
        writer
          .append(encode(n))
          .append("=")
          .append(encode(v))
    }
    writer
  }

  /** Map[String, String] representation of the [[Query]]
    *
    * If multiple values exist for a key, the first is returned. If
    * none exist, the empty `String` "" is returned.
    */
  lazy val params: Map[String, String] =
    CollectionCompat.mapValues(multiParams)(_.headOption.getOrElse(""))

  /** Map[String, Seq[String]] representation of the [[Query]]
    *
    * Params are represented as a `Seq[String]` and may be empty.
    */
  lazy val multiParams: Map[String, immutable.Seq[String]] =
    CollectionCompat.pairsToMultiParams(toVector)

  override def equals(that: Any): Boolean =
    that match {
      case that: Query => that.toVector == toVector
      case _ => false
    }

  override def hashCode: Int = 31 + toVector.##

  /////////////////////// QueryOps methods and types /////////////////////////
  override protected type Self = Query
  override protected val query: Query = this
  override protected def self: Self = this
  override protected def replaceQuery(query: Query): Self = query
  ////////////////////////////////////////////////////////////////////////////
}

object Query {
  type KeyValue = (String, Option[String])

  val empty: Query = new Query(Vector.empty)

  /*
   * "The characters slash ("/") and question mark ("?") may represent data
   * within the query component... it is sometimes better for usability to
   * avoid percent-encoding those characters."
   *   -- http://tools.ietf.org/html/rfc3986#section-3.4
   */
  private val NoEncode: CharPredicate = Uri.Unreserved ++ "?/"

  def apply(xs: (String, Option[String])*): Query =
    new Query(xs.toVector)

  def fromVector(xs: Vector[(String, Option[String])]): Query =
    new Query(xs)

  def fromPairs(xs: (String, String)*): Query =
    new Query(
      xs.toList.foldLeft(Vector.empty[KeyValue]) {
        case (m, (k, s)) => m :+ (k -> s.some)
      }
    )

  /** Generate a [[Query]] from its `String` representation
    *
    * If parsing fails, the empty [[Query]] is returned
    */
  def fromString(query: String): Query =
    if (query.isEmpty) new Query(Vector("" -> None))
    else
      QueryParser.parseQueryString(query) match {
        case Right(query) => query
        case Left(_) => Query.empty
      }

  /** Build a [[Query]] from the `Map` structure */
  def fromMap(map: collection.Map[String, collection.Seq[String]]): Query =
    new Query(map.foldLeft(Vector.empty[KeyValue]) {
      case (m, (k, Seq())) => m :+ (k -> None)
      case (m, (k, vs)) => vs.toList.foldLeft(m) { case (m, v) => m :+ (k -> v.some) }
    })
}
