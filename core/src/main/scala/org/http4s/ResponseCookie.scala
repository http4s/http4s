/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpCookie.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s

import org.http4s.util.{Renderable, Writer}
import scala.collection.{IterableLike, TraversableOnce, mutable}
import scala.collection.generic.CanBuildFrom

object RequestCookieJar {
  def empty: RequestCookieJar = new RequestCookieJar(Nil)

  def apply(cookies: RequestCookie*): RequestCookieJar = (newBuilder ++= cookies).result()

  /** The default builder for RequestCookieJar objects.
    */
  def newBuilder: mutable.Builder[RequestCookie, RequestCookieJar] =
    new mutable.Builder[RequestCookie, RequestCookieJar] {
      private[this] val coll = mutable.ListBuffer[RequestCookie]()
      def +=(elem: RequestCookie): this.type = {
        coll += elem
        this
      }

      def clear(): Unit = coll.clear()

      def result(): RequestCookieJar = new RequestCookieJar(Vector(coll.toSeq: _*))
    }

  implicit def canBuildFrom
    : CanBuildFrom[TraversableOnce[RequestCookie], RequestCookie, RequestCookieJar] =
    new CanBuildFrom[TraversableOnce[RequestCookie], RequestCookie, RequestCookieJar] {
      def apply(
          from: TraversableOnce[RequestCookie]
      ): mutable.Builder[RequestCookie, RequestCookieJar] = newBuilder

      def apply(): mutable.Builder[RequestCookie, RequestCookieJar] = newBuilder
    }

}
class RequestCookieJar(private val headers: Seq[RequestCookie])
    extends Iterable[RequestCookie]
    with IterableLike[RequestCookie, RequestCookieJar] {
  override protected[this] def newBuilder: mutable.Builder[RequestCookie, RequestCookieJar] =
    RequestCookieJar.newBuilder
  def iterator: Iterator[RequestCookie] = headers.iterator
  def empty: RequestCookieJar = RequestCookieJar.empty

  def get(key: String): Option[RequestCookie] = headers.find(_.name == key)
  def apply(key: String): RequestCookie = get(key).getOrElse(default(key))
  def contains(key: String): Boolean = headers.exists(_.name == key)
  def getOrElse(key: String, default: => String): RequestCookie =
    get(key).getOrElse(RequestCookie(key, default))
  override def seq: RequestCookieJar = this
  def default(key: String): RequestCookie =
    throw new NoSuchElementException("Can't find RequestCookie " + key)

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
  def cookies: Iterable[RequestCookie] = headers

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
    *
    *  @param  p   the predicate used to test keys
    *  @return an immutable map consisting only of those key value pairs of this map where the key satisfies
    *          the predicate `p`. The resulting map wraps the original map without copying any elements.
    */
  def filterKeys(p: String => Boolean): RequestCookieJar =
    new RequestCookieJar(headers.filter(c => p(c.name)))

  /* Overridden for efficiency. */
  override def toSeq: Seq[RequestCookie] = headers
  override def toBuffer[C >: RequestCookie]: mutable.Buffer[C] = {
    val result = new mutable.ArrayBuffer[C](size)
    copyToBuffer(result)
    result
  }

  override def equals(o: Any) =
    o match {
      case that: RequestCookieJar => this.headers == that.headers
      case _ => false
    }

  override def hashCode(): Int =
    headers.##

  override def toString(): String =
    s"RequestCookieJar(${map(_.renderString).mkString("\n")})"
}

// see http://tools.ietf.org/html/rfc6265
final case class RequestCookie(name: String, content: String) extends Renderable {

  override lazy val renderString: String = super.renderString

  override def render(writer: Writer): writer.type = {
    writer.append(name).append('=').append(content)
    writer
  }
}

final case class ResponseCookie(
    name: String,
    content: String,
    expires: Option[HttpDate] = None,
    maxAge: Option[Long] = None,
    domain: Option[String] = None,
    path: Option[String] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    extension: Option[String] = None
) extends Renderable {

  override lazy val renderString: String = super.renderString

  override def render(writer: Writer): writer.type = {
    writer.append(name).append('=').append(content)
    expires.foreach { e =>
      writer.append("; Expires=").append(e)
    }
    maxAge.foreach(writer.append("; Max-Age=").append(_))
    domain.foreach(writer.append("; Domain=").append(_))
    path.foreach(writer.append("; Path=").append(_))
    if (secure) writer.append("; Secure")
    if (httpOnly) writer.append("; HttpOnly")
    extension.foreach(writer.append("; ").append(_))
    writer
  }

  def clearCookie: headers.`Set-Cookie` =
    headers.`Set-Cookie`(copy(content = "", expires = Some(HttpDate.Epoch), maxAge = Some(0L)))
}
