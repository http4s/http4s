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
import org.http4s.Query._
import org.http4s.internal.{CollectionCompat, UriCoding}
import org.http4s.internal.parsing.Rfc3986
import org.http4s.parser.QueryParser
import org.http4s.util.{Renderable, Writer}

import scala.io.Codec
import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets

/** Collection representation of a query string
  *
  * It is a indexed sequence of key and maybe a value pairs which maps
  * precisely to a query string, modulo the identity of separators.
  *
  * When rendered, the resulting `String` will have the pairs separated
  * by '&' while the key is separated from the value with '='
  */
final class Query private[http4s] (val components: Vector[Component])
    extends QueryOps
    with Renderable {

  def apply(idx: Int): Component = components(idx)

  def length: Int = components.length

  def slice(from: Int, until: Int): Query = new Query(components.slice(from, until))

  def isEmpty: Boolean = components.isEmpty

  def nonEmpty: Boolean = components.nonEmpty

  def drop(n: Int): Query = new Query(components.drop(n))

  def dropRight(n: Int): Query = new Query(components.dropRight(n))

  def exists(f: Component => Boolean): Boolean =
    components.exists(f)

  def filterNot(f: Component => Boolean): Query =
    new Query(components.filterNot(f))

  def filter(f: Component => Boolean): Query =
    new Query(components.filter(f))

  def foreach(f: Component => Unit): Unit =
    components.foreach(f)

  def foldLeft[Z](z: Z)(f: (Z, Component) => Z): Z =
    components.foldLeft(z)(f)

  def foldRight[Z](z: Eval[Z])(f: (Component, Eval[Z]) => Eval[Z]): Eval[Z] =
    Foldable[Vector].foldRight(components, z)(f)

  def +:(elem: Component): Query =
    new Query(elem +: components)

  def :+(elem: Component): Query =
    new Query(components :+ elem)

  def ++(pairs: collection.Iterable[Component]): Query =
    new Query(this.components ++ pairs)

  def toVector: Vector[Component] = components

  def toList: List[Component] = toVector.toList

  /** Render the Query as a `String`.
    *
    * Pairs are separated by '&' and keys are separated from values by '='
    */
  override def render(writer: Writer): writer.type = {
    var first = true

    components.foreach { kv =>
      if (!first) writer.append(kv.separator)
      else first = false
      kv.render(writer)
    }
    writer
  }

  def normalize: Query = new Query(components.map(_.normalize))

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
    def separator: Char = '&'
    def key: String
    def value: Option[String]

    def normalize: Component

    override def equals(that: Any): Boolean = that match {
      case that: Component => that.renderString == renderString
      case _ => false
    }

    override def hashCode = renderString.##
  }
  object Component {
    sealed abstract class KeyValue extends Component {
      override def value: Some[String]
      override def normalize: KeyValue
    }

    object KeyValue {
      def apply(key: String, value: String): KeyValue = new KeyValueDecoded(key, value)
    }
    sealed abstract class KeyOnly extends Component {
      override val value: None.type = None
      override def normalize: KeyOnly
    }

    object KeyOnly {
      def apply(name: String): KeyOnly = new KeyOnlyDecoded(name)
      def unapply(wv: KeyOnly): Some[String] = Some(wv.key)
    }

    // TODO: remove indirection
    implicit val ord: Order[Component] = Order.by(kv => (kv.key, kv.value))

    def unapply(kv: Component): Some[(String, Option[String])] = Some(kv.key -> kv.value)

    def apply(key: String, value: String): KeyValue = new KeyValueDecoded(key, value)

    def apply(key: String, value: Option[String]): Component =
      value.fold[Component](new KeyOnlyDecoded(key))(new KeyValueDecoded(key, _))

    private[http4s] final class KeyValueDecoded(val key: String, v: String) extends KeyValue {
      override lazy val value: Some[String] = Some(v)
      override def normalize: KeyValue = this
      override def render(writer: Writer): writer.type =
        writer
          .append(encode(key))
          .append("=")
          .append(encode(v))
    }

    private[http4s] final class KeyValueEncoded(
        rawKey: String,
        rawVal: String,
        codec: Codec,
        override val separator: Char)
        extends KeyValue {
      override def key: String = decode(rawKey, codec)

      override def value: Some[String] = Some(decode(rawVal, codec))

      override def normalize: KeyValue = KeyValue(key, value.value)

      override def render(writer: Writer): writer.type =
        writer.append(rawKey).append("=").append(rawVal)
    }
    private[http4s] final class KeyOnlyDecoded(val key: String) extends KeyOnly {
      override def normalize: KeyOnly = this
      override def render(writer: Writer): writer.type =
        writer.append(encode(key))
    }

    private[http4s] final class KeyOnlyEncoded(
        override val renderString: String,
        override val separator: Char,
        codec: Codec)
        extends KeyOnly {
      override def key: String = decode(renderString, codec)
      override def normalize: KeyOnly = KeyOnly(key)

      override def render(writer: Writer): writer.type =
        writer.append(renderString)
    }

    private def encode(s: String) =
      UriCoding.encode(
        s,
        spaceIsPlus = false,
        charset = StandardCharsets.UTF_8,
        toSkip = UriCoding.QueryNoEncode)

    private def decode(str: String, codec: Codec): String =
      try Uri.decode(str, codec.charSet, plusIsSpace = true)
      catch {
        case _: IllegalArgumentException => ""
        case _: UnsupportedEncodingException => ""
      }
  }

  /** Represents the absence of a query string. */
  val empty: Query = new Query(Vector.empty)

  /** Represents a query string with no keys or values: `?` */
  val blank = new Query(Vector(Component("", None)))

  def apply(xs: (String, Option[String])*): Query =
    new Query(xs.toVector.map(kv => Component(kv._1, kv._2)))

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

  /* query       = *( pchar / "/" / "?" )
   *
   * These are illegal, but common in the wild.  We will be
   * "conservative in our sending behavior and liberal in our
   * receiving behavior", and encode them.
   */
  private[http4s] lazy val parser: Parser0[Query] = {
    import cats.parse.Parser.charIn
    import Rfc3986.pchar

    pchar.orElse(charIn("/?[]")).rep0.string.map(unsafeFromString)
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
