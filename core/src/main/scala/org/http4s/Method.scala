package org.http4s

import org.http4s.util.Registry

/**
 * An HTTP method.
 *
 * @param name the name of the method.
 */
sealed abstract case class Method (name: String) {
  override def toString = name

  def isSafe: Boolean
  def isIdempotent: Boolean

  /** Make a [[org.http4s.Request]] using this Method */
  def apply(uri: Uri): Request = Request(this, uri)

  /** Make a [[org.http4s.Request]] using this Method */
  def apply(uri: String): Request = {
    apply(Uri.fromString(uri)
      .getOrElse(throw new IllegalArgumentException(s"Invalid path: $uri")))
  }
}

object Method extends Registry {
  type Key = String
  type Value = Method

  implicit def fromKey(k: String): Method = notIdempotent(k)
  implicit def fromValue(m: Method): String = m.name

  private def notIdempotent(name: String): Method = new MethodImpl(name, false, false)
  private def idempotent(name: String): Method = new MethodImpl(name, false, true)
  private def safe(name: String): Method = new MethodImpl(name, true, true)

  private class MethodImpl(name: String, val isSafe: Boolean, val isIdempotent: Boolean) extends Method(name)

  val Options = registerValue(idempotent("OPTIONS"))
  val Get = registerValue(safe("GET"))
  val Head = registerValue(safe("HEAD"))
  val Post = registerValue(notIdempotent("POST"))
  val Put = registerValue(idempotent("PUT"))
  val Delete = registerValue(idempotent("DELETE"))
  val Trace = registerValue(idempotent("TRACE"))
  val Connect = registerValue(notIdempotent("CONNECT"))
  val Patch = registerValue(notIdempotent("PATCH"))

  /** Returns a set of all registered methods. */
  def methods: Iterable[Method] = registry.values
}