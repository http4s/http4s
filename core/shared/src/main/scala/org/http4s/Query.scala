/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.Eval
import cats.Foldable
import cats.Hash
import cats.Order
import cats.Show
import cats.parse.Parser0
import cats.syntax.all._
import org.http4s.Query._
import org.http4s.internal.CollectionCompat
import org.http4s.internal.UriCoding
import org.http4s.internal.parsing.Rfc3986
import org.http4s.parser.QueryParser
import org.http4s.util.Renderable
import org.http4s.util.Writer

import java.nio.charset.StandardCharsets
import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/** Collection representation of a query string
  *
  * It is a indexed sequence of key and maybe a value pairs which maps
  * precisely to a query string, modulo
  * [[https://datatracker.ietf.org/doc/html/rfc3986#section-2.1 percent-encoding]].
  *
  * When rendered, the resulting `String` will have the pairs separated
  * by '&' while the key is separated from the value with '='
  */
final class Query private (value: Either[Vector[KeyValue], String])
    extends QueryOps
    with Renderable {
  private[this] var _pairs: Vector[KeyValue] = null

  def pairs: Vector[KeyValue] = {
    if (_pairs == null) {
      _pairs = value.fold(identity, Query.parse)
    }
    _pairs
  }

  private def this(vec: Vector[KeyValue]) = this(Left(vec))

  @deprecated("Unsafe method. Use get(idx) instead", "0.23.17")
  def apply(idx: Int): KeyValue = pairs(idx)

  def get(idx: Int): Option[KeyValue] = pairs.get(idx.toLong)

  def length: Int = pairs.length

  def slice(from: Int, until: Int): Query = new Query(Left(pairs.slice(from, until)))

  def isEmpty: Boolean = pairs.isEmpty

  def nonEmpty: Boolean = pairs.nonEmpty

  def drop(n: Int): Query = new Query(Left(pairs.drop(n)))

  def dropRight(n: Int): Query = new Query(Left(pairs.dropRight(n)))

  def exists(f: KeyValue => Boolean): Boolean =
    pairs.exists(f)

  def filterNot(f: KeyValue => Boolean): Query =
    new Query(Left(pairs.filterNot(f)))

  def filter(f: KeyValue => Boolean): Query =
    new Query(Left(pairs.filter(f)))

  def foreach(f: KeyValue => Unit): Unit =
    pairs.foreach(f)

  def foldLeft[Z](z: Z)(f: (Z, KeyValue) => Z): Z =
    pairs.foldLeft(z)(f)

  def foldRight[Z](z: Eval[Z])(f: (KeyValue, Eval[Z]) => Eval[Z]): Eval[Z] =
    Foldable[Vector].foldRight(pairs, z)(f)

  def +:(elem: KeyValue): Query =
    new Query(Left(elem +: pairs))

  def :+(elem: KeyValue): Query =
    new Query(Left(pairs :+ elem))

  def ++(pairs: collection.Iterable[(String, Option[String])]): Query =
    new Query(Left(this.pairs ++ pairs))

  def toVector: Vector[(String, Option[String])] = pairs

  def toList: List[(String, Option[String])] = toVector.toList

  /** Render the Query as a `String`.
    *
    * Pairs are separated by '&' and keys are separated from values by '='
    */
  override def render(writer: Writer): writer.type = value.fold(
    { pairs =>
      var first = true

      def encode(s: String) =
        UriCoding.encode(
          s,
          spaceIsPlus = false,
          charset = StandardCharsets.UTF_8,
          toSkip = UriCoding.QueryNoEncode,
        )

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
    },
    raw => writer.append(raw),
  )

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
    if (toVector.isEmpty) Map.empty
    else {
      val m = mutable.Map.empty[String, ListBuffer[String]]
      toVector.foreach {
        case (k, None) => m.getOrElseUpdate(k, new ListBuffer)
        case (k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
      }
      CollectionCompat.mapValues(m.toMap)(_.toList)
    }

  /** Creates a new encoded `Self` with all the specified parameters in the [[Query]].
    * If the list of parameters is empty, it will return the original `Self`.
    */
  def encode: Self =
    if (query.toVector.isEmpty) {
      query
    } else {
      val result = query.toVector.map {
        case (k, None) => k -> None
        case (k, Some(v)) =>
          k -> Some(
            UriCoding.encode(
              v,
              StandardCharsets.UTF_8,
              spaceIsPlus = false,
              toSkip = UriCoding.QueryNoEncode,
            )
          )
      }

      Query.fromVector(result)
    }

  override def equals(that: Any): Boolean =
    that match {
      case that: Query => that.toVector == toVector
      case _ => false
    }

  override def hashCode: Int = 31 + toVector.##

  // ///////////////////// QueryOps methods and types /////////////////////////
  override protected type Self = Query
  override protected val query: Query = this
  override protected def self: Self = this
  override protected def replaceQuery(query: Query): Self = query
  // //////////////////////////////////////////////////////////////////////////
}

object Query {
  type KeyValue = (String, Option[String])

  /** Represents the absence of a query string. */
  val empty: Query = new Query(Vector.empty)

  /** Represents a query string with no keys or values: `?` */
  val blank = new Query(Vector("" -> None))

  def apply(xs: (String, Option[String])*): Query =
    new Query(xs.toVector)

  def fromVector(xs: Vector[(String, Option[String])]): Query =
    new Query(xs)

  def fromPairs(xs: (String, String)*): Query =
    new Query(
      xs.foldLeft(Vector.empty[KeyValue]) { case (m, (k, s)) =>
        m :+ (k -> Some(s))
      }
    )

  /** Generate a [[Query]] from its `String` representation
    *
    * If parsing fails, the empty [[Query]] is returned
    */
  def unsafeFromString(query: String): Query =
    if (query.isEmpty) new Query(Vector("" -> None))
    else
      QueryParser.parseQueryString(query) match {
        case Right(query) => query
        case Left(_) => Query.empty
      }

  @deprecated(message = "Use unsafeFromString instead", since = "0.22.0-M6")
  def fromString(query: String): Query =
    unsafeFromString(query)

  /** Build a [[Query]] from the `Map` structure */
  def fromMap(map: collection.Map[String, collection.Seq[String]]): Query =
    new Query(map.foldLeft(Vector.empty[KeyValue]) {
      case (m, (k, Seq())) => m :+ (k -> None)
      case (m, (k, vs)) => vs.foldLeft(m) { case (m, v) => m :+ (k -> Some(v)) }
    })

  private def parse(query: String): Vector[KeyValue] =
    if (query.isEmpty) blank.toVector
    else
      QueryParser.parseQueryStringVector(query) match {
        case Right(query) => query
        case Left(_) => Vector.empty
      }

  /** query       = *( pchar / "/" / "?" )
    *
    * These are illegal, but common in the wild.  We will be
    * "conservative in our sending behavior and liberal in our
    * receiving behavior", and encode them.
    */
  private[http4s] lazy val parser: Parser0[Query] = {
    import cats.parse.Parser.charIn
    import Rfc3986.pchar

    pchar.orElse(charIn("/?[]")).rep0.string.map(pchars => new Query(Right(pchars)))
  }

  implicit val catsInstancesForHttp4sQuery: Hash[Query] with Order[Query] with Show[Query] =
    new Hash[Query] with Order[Query] with Show[Query] {
      override def hash(x: Query): Int =
        x.hashCode

      override def compare(x: Query, y: Query): Int =
        x.toVector.compare(y.toVector)

      override def show(a: Query): String =
        a.renderString
    }
}
