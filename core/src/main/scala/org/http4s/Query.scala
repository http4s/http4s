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

import cats.parse.Parser0
import cats.syntax.all._
import cats.{Eval, Foldable, Hash, Order, Show}
import java.nio.charset.StandardCharsets
import org.http4s.Query._
import org.http4s.internal.{CollectionCompat, UriCoding}
import org.http4s.internal.parsing.Rfc3986
import org.http4s.parser.QueryParser
import org.http4s.util.{Renderable, Writer}

import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/** Collection representation of a query string
  *
  * It is a indexed sequence of key and maybe a value pairs which maps
  * precisely to a query string, the identity of separators.
  *
  * When rendered, the resulting `String` will have the pairs separated
  * by '&' while the key is separated from the value with '='
  */
final class Query private (value: Either[Vector[Component], String])
    extends QueryOps
    with Renderable {

  private[this] var _pairs: Vector[Component] = null

  def pairs: Vector[Component] = {
    if (_pairs == null) {
      _pairs = value.fold(identity, Query.parse)
    }
    _pairs
  }

  private def this(vec: Vector[Component]) = this(Left(vec))

  def apply(idx: Int): Component = pairs(idx)

  def length: Int = pairs.length

  def slice(from: Int, until: Int): Query = new Query(Left(pairs.slice(from, until)))

  def isEmpty: Boolean = pairs.isEmpty

  def nonEmpty: Boolean = pairs.nonEmpty

  def drop(n: Int): Query = new Query(Left(pairs.drop(n)))

  def dropRight(n: Int): Query = new Query(Left(pairs.dropRight(n)))

  def exists(f: Component => Boolean): Boolean =
    pairs.exists(f)

  def filterNot(f: Component => Boolean): Query =
    new Query(Left(pairs.filterNot(f)))

  def filter(f: Component => Boolean): Query =
    new Query(Left(pairs.filter(f)))

  def foreach(f: Component => Unit): Unit =
    pairs.foreach(f)

  def foldLeft[Z](z: Z)(f: (Z, Component) => Z): Z =
    pairs.foldLeft(z)(f)

  def foldRight[Z](z: Eval[Z])(f: (Component, Eval[Z]) => Eval[Z]): Eval[Z] =
    Foldable[Vector].foldRight(pairs, z)(f)

  def +:(elem: Component): Query =
    new Query(Left(elem +: pairs))

  def :+(elem: Component): Query =
    new Query(Left(pairs :+ elem))

  def ++(pairs: collection.Iterable[Component]): Query =
    new Query(Left(this.pairs ++ pairs))

  def toVector: Vector[Component] = pairs

  def toList: List[Component] = toVector.toList

  /** Render the Query as a `String`.
    *
    * Pairs are separated by '&' and keys are separated from values by '='
    */
  override def render(writer: Writer): writer.type = value.fold(
    { pairs =>
      var first = true

      pairs.foreach { kv =>
        if (!first) writer.append('&')
        else first = false
        kv.render(writer)
      }
      writer
    },
    raw => writer.append(raw)
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
        case Component(k, None) => m.getOrElseUpdate(k, new ListBuffer)
        case Component(k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
      }
      CollectionCompat.mapValues(m.toMap)(_.toList)
    }

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
  sealed abstract class Component extends Renderable {
    def key: String
    def value: Option[String]
  }

  private def encode(s: String) =
    UriCoding.encode(
      s,
      spaceIsPlus = false,
      charset = StandardCharsets.UTF_8,
      toSkip = UriCoding.QueryNoEncode)

  object Component {
    sealed abstract class KeyValue extends Component {
      override def value: Some[String]
    }

    object KeyValue {
      def apply(key: String, value: String): KeyValue = KeyValueImpl(key, value)
    }
    sealed abstract class KeyOnly extends Component {
      override def value: None.type
    }

    object KeyOnly {
      def apply(name: String): KeyOnly = KeyOnlyImpl(name)
      def unapply(wv: KeyOnly): Some[String] = Some(wv.key)
    }

    // TODO: remove indirection
    implicit val ord: Order[Component] = Order.by(kv => (kv.key, kv.value))

    def unapply(kv: Component): Some[(String, Option[String])] = Some(kv.key -> kv.value)

    def apply(key: String, value: String): KeyValue = KeyValueImpl(key, value)

    def apply(key: String, value: Option[String]): Component =
      value.fold[Component](KeyOnlyImpl(key))(KeyValueImpl(key, _))

    def withoutValue(key: String): KeyOnly = KeyOnlyImpl(key)
    private[Query] final case class KeyValueImpl(key: String, v: String) extends KeyValue {
      override lazy val value: Some[String] = Some(v)
      override def render(writer: Writer): writer.type =
        writer
          .append(encode(key))
          .append("=")
          .append(encode(v))
    }

    private[http4s] final case class KeyValueParsed(
        key: String,
        v: String,
        rawKey: String,
        rawVal: String)
        extends KeyValue {
      override lazy val value: Some[String] = Some(v)

      override def render(writer: Writer): writer.type =
        writer.append(rawKey).append("=").append(rawVal)
    }
    private[Query] final case class KeyOnlyImpl(key: String) extends KeyOnly {
      override val value: None.type = None
      override def render(writer: Writer): writer.type =
        writer.append(encode(key))
    }

    private[http4s] final case class KeyOnlyParsed(key: String, rawKey: String) extends KeyOnly {
      override val value: None.type = None
      override def render(writer: Writer): writer.type =
        writer.append(rawKey)
    }
  }

  /** Represents the absence of a query string. */
  val empty: Query = new Query(Vector.empty)

  /** Represents a query string with no keys or values: `?` */
  val blank = new Query(Vector(Component("", None)))

  def apply(xs: Component*): Query =
    new Query(xs.toVector)

  def fromVector(xs: Vector[Component]): Query =
    new Query(xs)

  def fromPairs(xs: (String, String)*): Query =
    new Query(
      xs.toList.foldLeft(Vector.empty[Component]) { case (m, (k, s)) =>
        m :+ Component(k, Some(s))
      }
    )

  /** Generate a [[Query]] from its `String` representation
    *
    * If parsing fails, the empty [[Query]] is returned
    */
  def unsafeFromString(query: String): Query =
    if (query.isEmpty) new Query(Vector(Component("", None)))
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
    new Query(map.foldLeft(Vector.empty[Component]) {
      case (m, (k, Seq())) => m :+ Component(k, None)
      case (m, (k, vs)) => vs.toList.foldLeft(m) { case (m, v) => m :+ Component(k, Some(v)) }
    })

  private def parse(query: String): Vector[Component] =
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
