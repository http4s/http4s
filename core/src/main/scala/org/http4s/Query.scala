package org.http4s

import org.http4s.Query._
import org.http4s.parser.QueryParser
import org.http4s.util.{Writer, Renderable}

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.IndexedSeq
import scala.collection.mutable.ListBuffer
import scala.collection.{ IndexedSeqOptimized, mutable }


final case class Query private(params: Vector[KeyValue])
  extends IndexedSeq[KeyValue]
  with IndexedSeqOptimized[KeyValue, Query]
  with QueryOps
  with Renderable 
{
  /////////////////////// QueryOps methods and types /////////////////////////

  override type Self = Query
  override protected val query: Query = this
  override protected def self: Self = this
  override protected def replaceQuery(query: Query): Self = query

  ////////////////////////////////////////////////////////////////////////////

  override def apply(idx: Int): KeyValue = params.apply(idx)

  override def length: Int = params.length

  override def +:[B >: KeyValue, That](elem: B)(implicit bf: CanBuildFrom[Query, B, That]): That = {
    if (bf eq Query.cbf) Query((elem +: params).asInstanceOf[Vector[KeyValue]]).asInstanceOf[That]
    else super.+:(elem)
  }

  override def :+[B >: KeyValue, That](elem: B)(implicit bf: CanBuildFrom[Query, B, That]): That = {
    if (bf eq Query.cbf) Query((params :+ elem).asInstanceOf[Vector[KeyValue]]).asInstanceOf[That]
    else super.:+(elem)
  }


  /** Base method for rendering this object efficiently */
  override def render(writer: Writer): writer.type = {
    var first = true
    params.foreach {
      case (n, None) =>
        if (!first) writer.append('&')
        else first = false
        writer.append(n)

      case (n, Some(v)) =>
        if (!first) writer.append('&')
        else first = false
        writer.append(n)
          .append("=")
          .append(v)
    }
    writer
  }

  def paramsView: Map[String, String] = new ParamsView(multiParams)

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
}

object Query {

  type KeyValue = (String, Option[String])

  val empty: Query = Query(Vector.empty)

  def fromPairs(xs: (String, String)*): Query = {
    val b = newBuilder
    xs.foreach{ case (k, v) => b += ((k, Some(v))) }
    b.result()
  }

  def fromOptions(xs: (String, Option[String])*): Query = {
    val b = newBuilder
    xs.foreach{ case (k, v) => b += ((k, v)) }
    b.result()
  }

  def fromString(query: String): Query = {
    if (query.isEmpty) Query(Vector("" -> None))
    else QueryParser.parseQueryString(query).getOrElse(Query.empty)
  }
  
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
