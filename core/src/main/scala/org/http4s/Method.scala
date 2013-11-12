package org.http4s

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

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

object Method extends ObjectRegistry[String, Method] {
  def notIdempotent(name: String): Method = new MethodImpl(name, false, false)
  def idempotent(name: String): Method = new MethodImpl(name, false, true)
  def safe(name: String): Method = new MethodImpl(name, true, true)

  private class MethodImpl(name: String, val isSafe: Boolean, val isIdempotent: Boolean) extends Method(name)

  private def register(method: Method): method.type = {
    registry.update(method.name, method)
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

  /**
   * Retrieves a method from the registry.
   *
   * @param name the name, case insensitive
   * @return the method, if registered
   */
  def get(name: String): Option[Method] = registry.get(name)

  /**
   * Retrieves a method from the registry, or creates an extension method otherwise.
   * The extension method is not registered, and assumed to be neither safe nor
   * idempotent.
   *
   * @param name the name, case insensitive
   * @return the method, if registered; otherwise, an extension method
   */
  def apply(name: String): Method = get(name).getOrElse(notIdempotent(name))
}