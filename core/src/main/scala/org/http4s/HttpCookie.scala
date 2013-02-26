package org.http4s

import util.DateTime
import collection.{TraversableOnce, mutable, IterableLike}
import collection.generic.CanBuildFrom

object RequestCookieJar {
  def empty = new RequestCookieJar(Nil)
  
  def apply(cookies: HttpCookie*): RequestCookieJar = (newBuilder ++= cookies).result()
  /** The default builder for RequestCookieJar objects.
   */
  def newBuilder: mutable.Builder[HttpCookie, RequestCookieJar] = new mutable.Builder[HttpCookie, RequestCookieJar] {
    private[this] val coll = mutable.ListBuffer[HttpCookie]()
    def +=(elem: HttpCookie): this.type = {
      coll += elem
      this
    }

    def clear() { coll.clear() }

    def result(): RequestCookieJar = new RequestCookieJar(Vector(coll.toSeq:_*))
  }
  
  
  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[HttpCookie], HttpCookie, RequestCookieJar] =
    new CanBuildFrom[TraversableOnce[HttpCookie], HttpCookie, RequestCookieJar] {
      def apply(from: TraversableOnce[HttpCookie]): mutable.Builder[HttpCookie, RequestCookieJar] =
        newBuilder ++= from
  
      def apply(): mutable.Builder[HttpCookie, RequestCookieJar] = newBuilder
    }
  
}
class RequestCookieJar(headers: Seq[HttpCookie]) extends Iterable[HttpCookie] with IterableLike[HttpCookie, RequestCookieJar] {
  override protected[this] def newBuilder: mutable.Builder[HttpCookie, RequestCookieJar] = RequestCookieJar.newBuilder
  def iterator: Iterator[HttpCookie] = headers.iterator
  def empty: RequestCookieJar = RequestCookieJar.empty
  
  def get(key: String): Option[HttpCookie] = headers.find(_.name == key)
  def apply(key: String): HttpCookie = get(key) getOrElse default(key)
  def contains(key: String): Boolean = headers.exists(_.name == key)
  def getOrElse(key: String, default: => String): HttpCookie = get(key) getOrElse HttpCookie(key, default)
  override def seq: RequestCookieJar = this
  def default(key: String): HttpCookie = throw new NoSuchElementException("Can't find cookie " + key)

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
  def cookies: Iterable[HttpCookie] = headers
  

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
  override def toSeq: Seq[HttpCookie] = headers
  override def toBuffer[C >: HttpCookie]: mutable.Buffer[C] = {
    val result = new mutable.ArrayBuffer[C](size)
    copyToBuffer(result)
    result
  }

  override def toString(): String = {
    s"RequestCookieJar(${map(_.value).mkString("\n")})"
  }
}


// see http://tools.ietf.org/html/rfc6265
case class HttpCookie(
  name: String,
  content: String,
  expires: Option[DateTime] = None,
  maxAge: Option[Long] = None,
  domain: Option[String] = None,
  path: Option[String] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  extension: Option[String] = None
) {
  def value: String = name + "=\"" + content + '"' +
                      expires.map("; Expires=" + _.toRfc1123DateTimeString).getOrElse("") +
                      maxAge.map("; Max-Age=" + _).getOrElse("") +
                      domain.map("; Domain=" + _).getOrElse("") +
                      path.map("; Path=" + _).getOrElse("") +
                      (if (secure) "; Secure" else "") +
                      (if (httpOnly) "; HttpOnly" else "") +
                      extension.map("; " + _).getOrElse("")

  override def toString = value
}
