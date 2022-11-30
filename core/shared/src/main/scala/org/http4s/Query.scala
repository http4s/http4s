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
import org.http4s.internal.UriCoding
import org.http4s.internal.parsing.Rfc3986
import org.http4s.parser.QueryParser
import org.http4s.util.Renderable
import org.http4s.util.Writer

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/** Representation of a query string.
  *
  * When a query is none, it is represented by the [[Query.Empty]].
  *
  * When a query is parsed – it is represented by the [[Query.Parsed]],
  * an indexed sequence of key and maybe value pairs
  * which maps precisely to a query string, modulo
  * [[https://datatracker.ietf.org/doc/html/rfc3986#section-2.1 percent-encoding]].
  * The resulting `String` will have the pairs separated
  * by '&' while the key is separated from the value with '='.
  *
  * Otherwise, a query is represented by the [[Query.Raw]] containing unparsed string.
  */

sealed trait Query extends QueryOps with Renderable {
  def pairs: Vector[KeyValue]

  def get(idx: Int): Option[KeyValue] = this match {
    case Query.Empty => None
    case parsed: Query.Parsed => parsed.pairs.get(idx.toLong)
    case raw: Query.Raw => raw.pairs.get(idx.toLong)
  }

  def length: Int = this match {
    case Query.Empty => 0
    case parsed: Query.Parsed => parsed.pairs.length
    case raw: Query.Raw => raw.pairs.length
  }

  def slice(from: Int, until: Int): Query = this match {
    case Query.Empty => this

    case parsed: Query.Parsed =>
      val sliced = parsed.pairs.slice(from, until)
      if (sliced.lengthIs == 0) Query.Empty
      else new Query.Parsed(sliced)

    case raw: Query.Raw =>
      val sliced = raw.pairs.slice(from, until)
      if (sliced.lengthIs == 0) Query.Empty
      else new Query.Parsed(sliced)
  }

  def isEmpty: Boolean = this match {
    case Query.Empty => true
    case parsed: Query.Parsed => parsed.pairs.isEmpty
    case raw: Query.Raw => raw.pairs.isEmpty
  }

  def nonEmpty: Boolean = !isEmpty

  def drop(n: Int): Query = this match {
    case Query.Empty => this

    case parsed: Query.Parsed =>
      val prepared = parsed.pairs.drop(n)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)

    case raw: Query.Raw =>
      val prepared = raw.pairs.drop(n)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)
  }

  def dropRight(n: Int): Query = this match {
    case Query.Empty => this

    case parsed: Query.Parsed =>
      val prepared = parsed.pairs.dropRight(n)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)

    case raw: Query.Raw =>
      val prepared = raw.pairs.dropRight(n)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)
  }

  def exists(f: KeyValue => Boolean): Boolean = this match {
    case Query.Empty => false
    case parsed: Query.Parsed => parsed.pairs.exists(f)
    case raw: Query.Raw => raw.pairs.exists(f)
  }

  def filter(f: KeyValue => Boolean): Query = this match {
    case Query.Empty => this

    case parsed: Query.Parsed =>
      val prepared = parsed.pairs.filter(f)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)

    case raw: Query.Raw =>
      val prepared = raw.pairs.filter(f)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)
  }

  def filterNot(f: KeyValue => Boolean): Query = this match {
    case Query.Empty => this

    case parsed: Query.Parsed =>
      val prepared = parsed.pairs.filterNot(f)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)

    case raw: Query.Raw =>
      val prepared = raw.pairs.filterNot(f)
      if (prepared.sizeIs == 0) Query.Empty
      else new Query.Parsed(prepared)
  }

  def foreach(f: KeyValue => Unit): Unit = this match {
    case Query.Empty => ()
    case parsed: Query.Parsed => parsed.pairs.foreach(f)
    case raw: Query.Raw => raw.pairs.foreach(f)
  }

  def foldLeft[Z](z: Z)(f: (Z, KeyValue) => Z): Z = this match {
    case Query.Empty => z
    case parsed: Query.Parsed => parsed.pairs.foldLeft(z)(f)
    case raw: Query.Raw => raw.pairs.foldLeft(z)(f)
  }

  def foldRight[Z](z: Eval[Z])(f: (KeyValue, Eval[Z]) => Eval[Z]): Eval[Z] =
    this match {
      case Query.Empty => z
      case parsed: Query.Parsed =>
        Foldable[Vector].foldRight(parsed.pairs, z)(f)
      case raw: Query.Raw =>
        Foldable[Vector].foldRight(raw.pairs, z)(f)
    }

  def +:(elem: KeyValue): Query = this match {
    case Query.Empty =>
      new Query.Parsed(Vector(elem))
    case parsed: Query.Parsed =>
      new Query.Parsed(elem +: parsed.pairs)
    case raw: Query.Raw =>
      new Query.Parsed(elem +: raw.pairs)
  }

  def :+(elem: KeyValue): Query = this match {
    case Query.Empty =>
      new Query.Parsed(Vector(elem))
    case parsed: Query.Parsed =>
      new Query.Parsed(parsed.pairs :+ elem)
    case raw: Query.Raw =>
      new Query.Parsed(raw.pairs :+ elem)
  }

  def ++(pairs: collection.Iterable[(String, Option[String])]): Query =
    this match {
      case Query.Empty =>
        new Query.Parsed(pairs.toVector)
      case parsed: Query.Parsed =>
        new Query.Parsed(parsed.pairs ++ pairs)
      case raw: Query.Raw =>
        new Query.Parsed(raw.pairs ++ pairs)
    }

  def toVector: Vector[(String, Option[String])] = this match {
    case Query.Empty => Vector.empty
    case parsed: Query.Parsed => parsed.pairs
    case raw: Query.Raw => raw.pairs
  }

  def toList: List[(String, Option[String])] = this match {
    case Query.Empty => List.empty
    case parsed: Query.Parsed => parsed.pairs.toList
    case raw: Query.Raw => raw.pairs.toList
  }

  /** Map[String, String] representation of the [[Query]]
    *
    * If multiple values exist for a key, the first is returned. If
    * none exist, the empty `String` "" is returned.
    */
  lazy val params: Map[String, String] = this match {
    case Query.Empty => Map.empty
    case _: Query.Parsed | _: Query.Raw =>
      multiParams.map { case (k, v) =>
        k -> v.headOption.getOrElse("")
      }
  }

  /** `Map[String, List[String]]` representation of the [[Query]]
    *
    * Params are represented as a `List[String]` and may be empty.
    */
  lazy val multiParams: Map[String, List[String]] = this match {
    case Query.Empty => Map.empty
    case _: Query.Parsed | _: Query.Raw =>
      val pairs = toVector

      if (pairs.isEmpty) Map.empty
      else {
        val m = mutable.Map.empty[String, ListBuffer[String]]
        pairs.foreach {
          case (k, None) => m.getOrElseUpdate(k, new ListBuffer)
          case (k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
        }
        m.view.mapValues(_.toList).toMap
      }
  }

  override protected type Self = Query
  override protected val query: Query = this
  override protected def self: Self = this
  override protected def replaceQuery(query: Query): Self = query
}

object Query {
  case object Empty extends Query {
    def pairs: Vector[KeyValue] = Vector.empty

    override def render(writer: Writer): writer.type =
      writer
  }

  final class Raw private[http4s] (value: String) extends Query {
    private[this] var _pairs: Vector[KeyValue] = _

    def pairs: Vector[KeyValue] = {
      if (_pairs == null) {
        _pairs = Query.parse(value)
      }
      _pairs
    }

    override def render(writer: Writer): writer.type =
      writer.append(value)

    override def equals(that: Any): Boolean =
      that match {
        case that: Query => that.toVector == toVector
        case _ => false
      }

    override def hashCode: Int = 31 + pairs.##
  }

  final class Parsed private[http4s] (value: Vector[KeyValue]) extends Query {
    def pairs: Vector[KeyValue] = value

    /** Render the Query as a `String`.
      *
      * Pairs are separated by '&' and keys are separated from values by '='
      */
    override def render(writer: Writer): writer.type = {
      var first = true

      def encode(s: String) =
        UriCoding.encode(
          s,
          spaceIsPlus = false,
          charset = StandardCharsets.UTF_8,
          toSkip = UriCoding.QueryNoEncode,
        )

      value.foreach {
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

    override def equals(that: Any): Boolean =
      that match {
        case that: Query => that.toVector == toVector
        case _ => false
      }

    override def hashCode: Int = 31 + value.##
  }

  type KeyValue = (String, Option[String])

  /** Represents the absence of a query string. */
  val empty: Query = Query.Empty

  /** Represents a query string with no keys or values: `?` */
  val blank = new Query.Parsed(Vector("" -> None))

  def apply(xs: (String, Option[String])*): Query =
    if (xs.sizeIs == 0) Query.Empty
    else new Query.Parsed(xs.toVector)

  def fromVector(xs: Vector[(String, Option[String])]): Query =
    if (xs.sizeIs == 0) Query.Empty
    else new Query.Parsed(xs)

  def fromPairs(xs: (String, String)*): Query =
    if (xs.sizeIs == 0) Query.Empty
    else
      new Query.Parsed(
        xs.toList.foldLeft(Vector.empty[KeyValue]) { case (m, (k, s)) =>
          m :+ (k -> Some(s))
        }
      )

  /** Generate a [[Query]] from its `String` representation
    *
    * If parsing fails, the empty [[Query]] is returned
    */
  def unsafeFromString(query: String): Query =
    if (query.isEmpty) new Query.Parsed(Vector("" -> None))
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
    new Query.Parsed(map.foldLeft(Vector.empty[KeyValue]) {
      case (m, (k, Seq())) => m :+ (k -> None)
      case (m, (k, vs)) => vs.toList.foldLeft(m) { case (m, v) => m :+ (k -> Some(v)) }
    })

  private def parse(query: String): Vector[KeyValue] =
    if (query.isEmpty) blank.toVector
    else
      QueryParser.parseQueryStringVector(query) match {
        case Right(query) => query
        case Left(_) => Vector.empty
      }

  /* query       = *( pchar / "/" / "?" )
   *
   * These are illegal, but common in the wild.  We will be
   * "conservative in our sending behavior and liberal in our
   * receiving behavior", and encode them.
   */
  private[http4s] lazy val parser: Parser0[Query] = {
    import cats.parse.Parser.charIn
    import Rfc3986.pchar

    pchar.orElse(charIn("/?[]")).rep0.string.map(pchars => new Query.Raw(pchars))
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
