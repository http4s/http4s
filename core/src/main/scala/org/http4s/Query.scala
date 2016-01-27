package org.http4s

import org.http4s.Query._
import org.http4s.parser.QueryParser
import org.http4s.util.{UrlFormCodec, UrlCodingUtils, Writer, Renderable}

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.{BitSet, IndexedSeq}
import scala.collection.mutable.ListBuffer
import scala.collection.{ IndexedSeqOptimized, mutable }

/** Collection representation of a query string
  *
  * It is a indexed sequence of key and maybe a value pairs which maps
  * precisely to a query string, modulo the identity of separators.
  *
  * When rendered, the resulting `String` will have the pairs separated
  * by '&' while the key is separated from the value with '='
  */
final class Query private(pairs: Vector[KeyValue])
  extends IndexedSeq[KeyValue]
  with IndexedSeqOptimized[KeyValue, Query]
  with QueryOps
  with Renderable 
{
  override def apply(idx: Int): KeyValue = pairs(idx)

  override def length: Int = pairs.length

  override def slice(from: Int, until: Int): Query = new Query(pairs.slice(from, until))

  override def +:[B >: KeyValue, That](elem: B)(implicit bf: CanBuildFrom[Query, B, That]): That = {
    if (bf eq Query.cbf) new Query((elem +: pairs).asInstanceOf[Vector[KeyValue]]).asInstanceOf[That]
    else super.+:(elem)
  }

  override def :+[B >: KeyValue, That](elem: B)(implicit bf: CanBuildFrom[Query, B, That]): That = {
    if (bf eq Query.cbf) new Query((pairs :+ elem).asInstanceOf[Vector[KeyValue]]).asInstanceOf[That]
    else super.:+(elem)
  }

  override def toVector: Vector[(String, Option[String])] = pairs

  /** Render the Query as a `String`.
    *
    * Pairs are separated by '&' and keys are separated from values by '='
    */
  override def render(writer: Writer): writer.type = {
    var first = true
    def encode(s: String) =
      UrlCodingUtils.urlEncode(s, spaceIsPlus = false, toSkip = NoEncode)
    pairs.foreach {
      case (n, None) =>
        if (!first) writer.append('&')
        else first = false
        writer.append(encode(n))

      case (n, Some(v)) =>
        if (!first) writer.append('&')
        else first = false
        writer.append(encode(n))
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
  def params: Map[String, String] = new ParamsView(multiParams)

  /** Map[String, Seq[String] ] representation of the [[Query]]
    *
    * Params are represented as a `Seq[String]` and may be empty.
    */
  lazy val multiParams: Map[String, Seq[String]] = {
    if (isEmpty) Map.empty[String, Seq[String]]
    else {
      val m = mutable.Map.empty[String, ListBuffer[String]]
      foreach {
        case (k, None) => m.getOrElseUpdate(k, new ListBuffer)
        case (k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
      }

      m.toMap
    }
  }

  override protected[this] def newBuilder: mutable.Builder[KeyValue, Query] = Query.newBuilder

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
  private val NoEncode: BitSet =
    UrlFormCodec.urlUnreserved ++ Set('?', '/').map(_.toInt)

  def apply(xs: (String, Option[String])*): Query =
    new Query(xs.toVector)

  def fromPairs(xs: (String, String)*): Query = {
    val b = newBuilder
    xs.foreach{ case (k, v) => b += ((k, Some(v))) }
    b.result()
  }

  /** Generate a [[Query]] from its `String` representation
    *
    * If parsing fails, the empty [[Query]] is returned
    */
  def fromString(query: String): Query = {
    if (query.isEmpty) new Query(Vector("" -> None))
    else QueryParser.parseQueryString(query).getOrElse(Query.empty)
  }

  /** Build a [[Query]] from the `Map` structure */
  def fromMap(map: Map[String, Seq[String]]): Query = {
    val b = newBuilder
    map.foreach {
      case (k, Seq()) => b +=  ((k, None))
      case (k, vs)    => vs.foreach(v => b += ((k, Some(v))))
    }
    b.result()
  }

  def newBuilder: mutable.Builder[KeyValue, Query] =
    Vector.newBuilder[KeyValue].mapResult(v => new Query(v))

  implicit val cbf: CanBuildFrom[Query, KeyValue, Query] = new CanBuildFrom[Query, KeyValue, Query] {
    override def apply(from: Query): mutable.Builder[KeyValue, Query] = newBuilder
    override def apply(): mutable.Builder[KeyValue, Query] = newBuilder
  }

  ///////////////////////////////////////////////////////////////////////
  // Wrap the multiParams to get a Map[String, String] view
  private class ParamsView(wrapped: Map[String, Seq[String]]) extends Map[String, String] {
    override def +[B1 >: String](kv: (String, B1)): Map[String, B1] = {
      val m = wrapped + (kv)
      m.asInstanceOf[Map[String, B1]]
    }

    override def -(key: String): Map[String, String] = new ParamsView(wrapped - key)

    override def iterator: Iterator[(String, String)] =
      wrapped.iterator.map { case (k, s) => (k, s.headOption.getOrElse("")) }

    override def get(key: String): Option[String] =
      wrapped.get(key).flatMap(_.headOption)
  }
}
