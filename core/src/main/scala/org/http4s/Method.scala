package org.http4s

import org.http4s.util.Resolvable

/**
 * An HTTP method.
 *
 * @param name the name of the method.
 */
sealed abstract case class Method (name: String) {
  override def toString = name

  def isSafe: Boolean
  def isIdempotent: Boolean

//  def :/(path: String): Path = new :/(this, Path(path))
//  def /(path: String): Path = new /(this, Path(path))
}

object Method extends Resolvable[String, Method] {
  protected def stringToRegistryKey(s: String): String = s

  protected def fromKey(k: String): Method = notIdempotent(k)

  private def notIdempotent(name: String): Method = new MethodImpl(name, false, false)
  private def idempotent(name: String): Method = new MethodImpl(name, false, true)
  private def safe(name: String): Method = new MethodImpl(name, true, true)

  private class MethodImpl(name: String, val isSafe: Boolean, val isIdempotent: Boolean) extends Method(name)

  private def register(method: Method): method.type = {
    register(method.name, method)
    method
  }

  val Options = register(idempotent("OPTIONS"))
  val Get = register(safe("GET"))
  val Head = register(safe("HEAD"))
  val Post = register(notIdempotent("POST"))
  val Put = register(idempotent("PUT"))
  val Delete = register(idempotent("DELETE"))
  val Trace = register(idempotent("TRACE"))
  val Connect = register(notIdempotent("CONNECT"))
  val Patch = register(notIdempotent("PATCH"))

  /**
   * Returns a set of all registered methods.
   */
  def methods: Iterable[Method] = registry.values
}