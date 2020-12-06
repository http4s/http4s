/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

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
