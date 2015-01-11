package org.http4s

import org.http4s.Query._
import org.http4s.parser.QueryParser
import org.http4s.util.{Writer, Renderable}

import scala.collection.generic.CanBuildFrom
import scala.collection.immutable.IndexedSeq
import scala.collection.mutable.ListBuffer
import scala.collection.{ IndexedSeqOptimized, mutable }


final case class Query private(params: Vector[KV])
  extends IndexedSeq[KV] 
  with IndexedSeqOptimized[KV, Query]
  with Renderable 
{

  override def apply(idx: Int): KV = params.apply(idx)

  override def length: Int = params.length

  override def +:[B >: KV, That](elem: B)(implicit bf: CanBuildFrom[Query, B, That]): That = {
    if (bf eq Query.cbf) Query((elem +: params).asInstanceOf[Vector[KV]]).asInstanceOf[That]
    else super.+:(elem)
  }

  override def :+[B >: KV, That](elem: B)(implicit bf: CanBuildFrom[Query, B, That]): That = {
    if (bf eq Query.cbf) Query((params :+ elem).asInstanceOf[Vector[KV]]).asInstanceOf[That]
    else super.:+(elem)
  }


  /** Base method for rendering this object efficiently */
  override def render(writer: Writer): writer.type = {
    var first = true
    params.foreach {
      case KV(n, None) =>
        if (!first) writer.append('&')
        else first = false
        writer.append(n)

      case KV(n, Some(v)) =>
        if (!first) writer.append('&')
        else first = false
        writer.append(n)
          .append("=")
          .append(v)
    }
    writer
  }

  def asMap: Map[String, Seq[String]] = {
    if (isEmpty) Map.empty[String, Seq[String]]
    else {
      val m = mutable.Map.empty[String, ListBuffer[String]]
      foreach {
        case KV(k, None) => m.getOrElseUpdate(k, new ListBuffer)
        case KV(k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
      }

      m.toMap
    }
  }

  override protected[this] def newBuilder: mutable.Builder[KV, Query] = Query.newBuilder
}

object Query {
  
  case class KV(key: String, value: Option[String])

  val empty: Query = Query(Vector.empty)

  def fromPairs(xs: (String, String)*): Query = {
    val b = newBuilder
    xs.foreach{ case (k, v) => b += KV(k, Some(v)) }
    b.result()
  }

  def fromOptions(xs: (String, Option[String])*): Query = {
    val b = newBuilder
    xs.foreach{ case (k, v) => b += KV(k, v) }
    b.result()
  }

  def fromString(query: String): Query = {
    if (query.isEmpty) Query(Vector(KV("", None)))
    else QueryParser.parseQueryString(query).getOrElse(Query.empty)
  }
  
  def fromMap(map: Map[String, Seq[String]]): Query = {
    val b = newBuilder
    map.foreach {
      case (k, Seq()) => b +=  KV(k, None)
      case (k, vs)    => vs.foreach(v => b += KV(k, Some(v)))
    }
    b.result()
  }

  def newBuilder: mutable.Builder[KV, Query] =
    Vector.newBuilder[KV].mapResult(v => new Query(v))

  implicit val cbf: CanBuildFrom[Query, KV, Query] = new CanBuildFrom[Query, KV, Query] {
    override def apply(from: Query): mutable.Builder[KV, Query] = newBuilder
    override def apply(): mutable.Builder[KV, Query] = newBuilder
  }
}
