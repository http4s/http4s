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

object RequestCookieJar {
  def empty: RequestCookieJar = new RequestCookieJar(Nil)

  def apply(cookies: RequestCookie*): RequestCookieJar = new RequestCookieJar(cookies.toList)
}

class RequestCookieJar private (val cookies: List[RequestCookie]) extends AnyVal {
  def iterator: Iterator[RequestCookie] = cookies.iterator
  def empty: RequestCookieJar = RequestCookieJar.empty

  def get(key: String): Option[RequestCookie] = cookies.find(_.name == key)
  def apply(key: String): RequestCookie = get(key).getOrElse(default(key))
  def contains(key: String): Boolean = cookies.exists(_.name == key)
  def getOrElse(key: String, default: => String): RequestCookie =
    get(key).getOrElse(RequestCookie(key, default))
  def default(key: String): RequestCookie =
    throw new NoSuchElementException("Can't find RequestCookie " + key)

  def keySet: Set[String] = cookies.map(_.name).toSet

  /** Collects all keys of this map in an iterable collection.
    *
    *  @return the keys of this map as an iterable.
    */
  def keys: Iterable[String] = keySet

  /** Collects all values of this map in an iterable collection.
    *
    *  @return the values of this map as an iterable.
    */
  def values: Iterable[String] = cookies.map(_.content)

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
    new RequestCookieJar(cookies.filter(c => p(c.name)))

  override def toString(): String =
    s"RequestCookieJar(${cookies.map(_.renderString).mkString("\n")})"

  def ++(cookies: collection.Iterable[RequestCookie]) =
    new RequestCookieJar(this.cookies ++ cookies)
}

// see http://tools.ietf.org/html/rfc6265
final case class RequestCookie(name: String, content: String) extends Renderable {
  override lazy val renderString: String = super.renderString

  override def render(writer: Writer): writer.type = {
    writer.append(name).append('=').append(content)
    writer
  }
}

/**
  * @param extension The extension attributes of the cookie.  If there is more
  * than one, they are joined by semi-colon, which must not appear in an
  * attribute value.
  */
final case class ResponseCookie(
    name: String,
    content: String,
    expires: Option[HttpDate] = None,
    maxAge: Option[Long] = None,
    domain: Option[String] = None,
    path: Option[String] = None,
    sameSite: SameSite = SameSite.Lax,
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
    writer.append("; SameSite=").append(sameSite)
    if (secure || sameSite == SameSite.None) writer.append("; Secure")
    if (httpOnly) writer.append("; HttpOnly")
    extension.foreach(writer.append("; ").append(_))
    writer
  }

  def clearCookie: headers.`Set-Cookie` =
    headers.`Set-Cookie`(copy(content = "", expires = Some(HttpDate.Epoch), maxAge = Some(0L)))
}
