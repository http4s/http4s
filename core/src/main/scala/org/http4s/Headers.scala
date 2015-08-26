package org.http4s

import org.http4s.HeaderKey.StringKey
import org.http4s.util.CaseInsensitiveString
import org.http4s.headers.`Set-Cookie`

import scala.collection.{GenTraversableOnce, immutable, mutable}
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer


/** A collection of HTTP Headers */
final class Headers private (headers: List[Header])
  extends immutable.Iterable[Header]
  with collection.IterableLike[Header, Headers]
{

  override def toList: List[Header] = headers

  override def isEmpty: Boolean = headers.isEmpty

  override protected def newBuilder: mutable.Builder[Header, Headers] = Headers.newBuilder

  override def drop(n: Int): Headers = if (n == 0) this else new Headers(headers.drop(n))

  override def head: Header = headers.head

  override def foreach[B](f: Header => B): Unit = headers.foreach(f)

  def iterator: Iterator[Header] = headers.iterator

  /** Attempt to get a [[org.http4s.Header]] of type key.HeaderT from this collection
    *
    * @param key [[HeaderKey.Extractable]] that can identify the required header
    * @return a scala.Option possibly containing the resulting header of type key.HeaderT
  *   @see [[Header]] object and get([[CaseInsensitiveString]])
    */
  def get(key: HeaderKey.Extractable): Option[key.HeaderT] = key.from(this)

  /** Attempt to get a [[org.http4s.Header]] from this collection of headers
    *
    * @param key name of the header to find
    * @return a scala.Option possibly containing the resulting [[org.http4s.Header]]
    *
    * @see [[HeaderKey.Default]] in conjunction with get([[HeaderKey]])
    */
  def get(key: CaseInsensitiveString): Option[Header] = headers.find(_.name == key)

  /** Make a new collection adding the specified headers, replacing existing headers of singleton type
    * The passed headers are assumed to contain no duplicate Singleton headers.
    *
    * @param in multiple [[Header]] to append to the new collection
    * @return a new [[Headers]] containing the sum of the initial and input headers
    */
  def put(in: Header*): Headers = {
    if (in.isEmpty) this
    else if (this.isEmpty) new Headers(in.toList)
    else this ++ in
  }

  /** Concatenate the two collections
    * If the resulting collection is of Headers type, duplicate Singleton headers will be removed from
    * this Headers collection.
    *
    * @param that collection to append
    * @tparam B type contained in collection `that`
    * @tparam That resulting type of the new collection
    */
  override def ++[B >: Header, That](that: GenTraversableOnce[B])(implicit bf: CanBuildFrom[Headers, B, That]): That = {
    if (bf eq Headers.canBuildFrom) {   // Making a new Headers collection from a collection of Header's
      if (that.isEmpty) this.asInstanceOf[That]
      else if (this.isEmpty) that match {
        case hs: Headers => hs.asInstanceOf[That]
        case hs => new Headers(hs.toList.asInstanceOf[List[Header]]).asInstanceOf[That]
      }
      else {
        val hs = that.toList.asInstanceOf[List[Header]]
        val acc = new ListBuffer[Header]
        this.headers.foreach { orig => orig.parsed match {
          case h: Header.Recurring                 => acc += orig
          case h: `Set-Cookie`                     => acc += orig
          case h if !hs.exists(_.name == h.name)   => acc += orig
          case _                                   => // NOOP, drop non recurring header that already exists
        }}

        val h =  new Headers(acc.prependToList(hs))
        h.asInstanceOf[That]
      }
    }
    else super.++(that)
  }
}

object Headers {
  val empty = apply()

  /** Create a new Headers collection from the headers */
  def apply(headers: Header*): Headers = Headers(headers.toList)

  /** Create a new Headers collection from the headers */
  def apply(headers: List[Header]): Headers = new Headers(headers)

  implicit val canBuildFrom: CanBuildFrom[Traversable[Header], Header, Headers] =
    new CanBuildFrom[TraversableOnce[Header], Header, Headers] {
      def apply(from: TraversableOnce[Header]): mutable.Builder[Header, Headers] = newBuilder

      def apply(): mutable.Builder[Header, Headers] = newBuilder
    }

  private def newBuilder: mutable.Builder[Header, Headers] =
    new mutable.ListBuffer[Header] mapResult (b => new Headers(b))
}
