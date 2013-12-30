package org.http4s

import collection.{TraversableOnce, mutable, IterableLike}
import collection.generic.CanBuildFrom
import org.joda.time.DateTime
import org.http4s.util.Renderable

object RequestCookieJar {
  def empty = new RequestCookieJar(Nil)
  
  def apply(cookies: Cookie*): RequestCookieJar = (newBuilder ++= cookies).result()
  /** The default builder for RequestCookieJar objects.
   */
  def newBuilder: mutable.Builder[Cookie, RequestCookieJar] = new mutable.Builder[Cookie, RequestCookieJar] {
    private[this] val coll = mutable.ListBuffer[Cookie]()
    def +=(elem: Cookie): this.type = {
      coll += elem
      this
    }

    def clear() { coll.clear() }

    def result(): RequestCookieJar = new RequestCookieJar(Vector(coll.toSeq:_*))
  }
  
  
  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Cookie], Cookie, RequestCookieJar] =
    new CanBuildFrom[TraversableOnce[Cookie], Cookie, RequestCookieJar] {
      def apply(from: TraversableOnce[Cookie]): mutable.Builder[Cookie, RequestCookieJar] =
        newBuilder ++= from
  
      def apply(): mutable.Builder[Cookie, RequestCookieJar] = newBuilder
    }
  
}
class RequestCookieJar(headers: Seq[Cookie]) extends Iterable[Cookie] with IterableLike[Cookie, RequestCookieJar] {
  override protected[this] def newBuilder: mutable.Builder[Cookie, RequestCookieJar] = RequestCookieJar.newBuilder
  def iterator: Iterator[Cookie] = headers.iterator
  def empty: RequestCookieJar = RequestCookieJar.empty
  
  def get(key: String): Option[Cookie] = headers.find(_.name == key)
  def apply(key: String): Cookie = get(key) getOrElse default(key)
  def contains(key: String): Boolean = headers.exists(_.name == key)
  def getOrElse(key: String, default: => String): Cookie = get(key) getOrElse Cookie(key, default)
  override def seq: RequestCookieJar = this
  def default(key: String): Cookie = throw new NoSuchElementException("Can't find cookie " + key)

  def keySet: Set[String] = headers.map(_.name).toSet

  /** Collects all keys of this map in an iterable collection.
   *
   *  @return the keys of this map as an iterable.
   */
  def keys: Iterable[String] = keySet

  /** Collects all values of this map in an iterable collection.
   *
   *  @return the values of this map as an iterable.
   */
  def values: Iterable[String] = headers.map(_.content) 
  
  /** Collects all values of this map in an iterable collection.
   *
   *  @return the values of this map as an iterable.
   */
  def cookies: Iterable[Cookie] = headers
  

  /** Creates an iterator for all keys.
   *
   *  @return an iterator over all keys.
   */
  def keysIterator: Iterator[String] = keys.iterator

  /** Creates an iterator for all values in this map.
   *
   *  @return an iterator over all values that are associated with some key in this map.
   */
  def valuesIterator: Iterator[Any] = values.iterator

  /** Filters this map by retaining only keys satisfying a predicate.
   *  @param  p   the predicate used to test keys
   *  @return an immutable map consisting only of those key value pairs of this map where the key satisfies
   *          the predicate `p`. The resulting map wraps the original map without copying any elements.
   */
  def filterKeys(p: String => Boolean): RequestCookieJar = new RequestCookieJar(headers.filter(c => p(c.name)))

  /* Overridden for efficiency. */
  override def toSeq: Seq[Cookie] = headers
  override def toBuffer[C >: Cookie]: mutable.Buffer[C] = {
    val result = new mutable.ArrayBuffer[C](size)
    copyToBuffer(result)
    result
  }

  override def toString(): String = {
    s"RequestCookieJar(${map(_.value).mkString("\n")})"
  }
}


// see http://tools.ietf.org/html/rfc6265
case class Cookie(
  name: String,
  content: String,
  expires: Option[DateTime] = None,
  maxAge: Option[Long] = None,
  domain: Option[String] = None,
  path: Option[String] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  extension: Option[String] = None
) extends Renderable {

//  def value: String = name + "=\"" + content + '"' +
//                      expires.map("; Expires=" + _.formatRfc1123).getOrElse("") +
//                      maxAge.map("; Max-Age=" + _).getOrElse("") +
//                      domain.map("; Domain=" + _).getOrElse("") +
//                      path.map("; Path=" + _).getOrElse("") +
//                      (if (secure) "; Secure" else "") +
//                      (if (httpOnly) "; HttpOnly" else "") +
//                      extension.map("; " + _).getOrElse("")
  override lazy val value: String = super.value

  def render(builder: StringBuilder): StringBuilder = {
    builder.append(name).append("=\"").append(content).append('"')
    expires.foreach{ e => builder.append("; Expires=").append(e.formatRfc1123) }
    maxAge.foreach(builder.append("; Max-Age=").append(_))
    domain.foreach(builder.append("; Domain=").append(_))
    path.foreach(builder.append("; Path=").append(_))
    if (secure) builder.append("; Secure")
    if (httpOnly) builder.append("; HttpOnly")
    extension.foreach(builder.append("; ").append(_))
    builder
  }

  override def toString = value
}
