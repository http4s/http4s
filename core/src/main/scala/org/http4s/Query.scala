package org.http4s

import org.http4s.FormQuery._
import org.http4s.util._
import org.http4s.util.encoding.UriCodingUtils

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.IndexedSeq
import scala.collection.mutable.ListBuffer
import scala.collection.{IndexedSeqOptimized, mutable}


sealed trait Query extends QueryOps with Renderable {
  def asForm: FormQuery
  def isEmpty: Boolean
  def encoded: String
}

/** Represents the absence of a query component */
object EmptyQuery extends Query {
  override def asForm: FormQuery = FormQuery()
  override def encoded: String = "<please check isEmpty before using this value>"
  override def isEmpty: Boolean = true

  /////////////////////// QueryOps methods and types /////////////////////////
  override protected type Self = FormQuery
  override protected def self: Self = formQuery
  override protected def replaceQuery(query: FormQuery): Self = query
  override protected val formQuery: FormQuery = asForm
  ////////////////////////////////////////////////////////////////////////////

  /** Base method for rendering this object efficiently */
  override def render(writer: Writer): writer.type = writer << "EmptyQuery"
}

object Query {
  def apply(s: String): PlainQuery = new PlainQuery(s)
  def apply(xs: (String, Option[String])*): FormQuery = FormQuery(xs: _*)

  val empty: Query = EmptyQuery

  def fromString(s: String): PlainQuery = new PlainQuery(s)
  def fromPairs(xs: (String, String)*): FormQuery = FormQuery.fromPairs(xs: _*)
  def fromMap(map: Map[String, Seq[String]]): FormQuery = FormQuery.fromMap(map)

  def equals(q1: Query, q2: Query): Boolean =
    (q1.isEmpty && q2.isEmpty) || (!q1.isEmpty && !q2.isEmpty && q1.encoded == q2.encoded)

}

final case class PlainQuery(plain: String) extends Query with QueryOps with Renderable {
  def asForm: FormQuery = FormQuery.fromString(plain)
  def isEmpty: Boolean = false

  /** Base method for rendering this object efficiently */
  override def render(writer: Writer): writer.type =
    writer << encoded

  def encoded = UriCodingUtils.encodePlainQueryString(plain).encoded

  /////////////////////// QueryOps methods and types /////////////////////////
  override protected type Self = FormQuery
  override protected def self: Self = formQuery
  override protected def replaceQuery(query: FormQuery): Self = query
  override protected lazy val formQuery: FormQuery = asForm
  ////////////////////////////////////////////////////////////////////////////

  override def equals(obj: Any): Boolean = obj match {
    case null => false
    case that: Query => Query.equals(this, that)
    case _ => false
  }
}

object PlainQuery {
  def empty: PlainQuery = new PlainQuery("")
//  def apply(s: String): PlainQuery =
}

/** Collection representation of a query string
  *
  * It is a indexed sequence of key and maybe a value pairs which maps
  * precisely to a query string, modulo the identity of separators.
  *
  * When rendered, the resulting `String` will have the pairs separated
  * by '&' while the key is separated from the value with '='
  */
final case class FormQuery(pairs: Vector[KeyValue])
  extends Query
  with IndexedSeq[KeyValue]
  with IndexedSeqOptimized[KeyValue, FormQuery]
  with QueryOps
  with Renderable
{
  override def asForm: FormQuery = this

  override def apply(idx: Int): KeyValue = pairs(idx)

  override def length: Int = pairs.length

  override def slice(from: Int, until: Int): FormQuery = new FormQuery(pairs.slice(from, until))

  override def +:[B >: KeyValue, That](elem: B)(implicit bf: CanBuildFrom[FormQuery, B, That]): That = {
    if (bf eq FormQuery.cbf) new FormQuery((elem +: pairs).asInstanceOf[Vector[KeyValue]]).asInstanceOf[That]
    else super.+:(elem)
  }

  override def :+[B >: KeyValue, That](elem: B)(implicit bf: CanBuildFrom[FormQuery, B, That]): That = {
    if (bf eq FormQuery.cbf) new FormQuery((pairs :+ elem).asInstanceOf[Vector[KeyValue]]).asInstanceOf[That]
    else super.:+(elem)
  }

  override def toVector: Vector[(String, Option[String])] = pairs

  /** Render the FormQuery as a `String`.
    *
    * Pairs are separated by '&' and keys are separated from values by '='
    */
  override def render(writer: Writer): writer.type =
    writer << encoded

  def encoded: String = UriCodingUtils.encodeQueryVector(pairs).encoded
  override def toString = super[Renderable].toString

  /** Map[String, String] representation of the [[FormQuery]]
    *
    * If multiple values exist for a key, the first is returned. If
    * none exist, the empty `String` "" is returned.
    */
  def params: Map[String, String] = new ParamsView(multiParams)

  /** Map[String, Seq[String] ] representation of the [[FormQuery]]
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

  override protected[this] def newBuilder: mutable.Builder[KeyValue, FormQuery] = FormQuery.newBuilder

  /////////////////////// QueryOps methods and types /////////////////////////
  override protected type Self = FormQuery
  override protected val formQuery: FormQuery = this
  override protected def self: Self = this
  override protected def replaceQuery(query: FormQuery): Self = query
  ////////////////////////////////////////////////////////////////////////////

  override def equals(obj: Any): Boolean = obj match {
    case null => false
    case that: Query => Query.equals(this, that)
    case _ => super.equals(obj) // delegate to IndexedSeq or whatever since we have tests that expect to be able to compare FormQuery to Seq
  }
}

object FormQuery {
  type KeyValue = (String, Option[String])

  def apply(xs: (String, Option[String])*): FormQuery =
    new FormQuery(xs.toVector)

  def fromPairs(xs: (String, String)*): FormQuery = {
    val b = newBuilder
    xs.foreach{ case (k, v) => b += ((k, Some(v))) }
    b.result()
  }

  /** Generate a [[FormQuery]] from its `String` representation
    *
    * If parsing fails, the empty [[FormQuery]] is returned
    */
  def fromString(formEncodedString: String): FormQuery = {
    if (formEncodedString.isEmpty) new FormQuery(Vector("" -> None))
    else FormQuery(UriCodingUtils.w3cHtml5FormUrlDecode(formEncodedString))
      //QueryParser.parseQueryString(formQuery).getOrElse(FormQuery.empty)
  }

  /** Build a [[FormQuery]] from the `Map` structure */
  def fromMap(map: Map[String, Seq[String]]): FormQuery = {
    val b = newBuilder
    map.foreach {
      case (k, Seq()) => b +=  ((k, None))
      case (k, vs)    => vs.foreach(v => b += ((k, Some(v))))
    }
    b.result()
  }

  def newBuilder: mutable.Builder[KeyValue, FormQuery] =
    Vector.newBuilder[KeyValue].mapResult(v => new FormQuery(v))

  implicit val cbf: CanBuildFrom[FormQuery, KeyValue, FormQuery] = new CanBuildFrom[FormQuery, KeyValue, FormQuery] {
    override def apply(from: FormQuery): mutable.Builder[KeyValue, FormQuery] = newBuilder
    override def apply(): mutable.Builder[KeyValue, FormQuery] = newBuilder
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
