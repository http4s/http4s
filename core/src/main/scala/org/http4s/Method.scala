package org.http4s

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

/**
 * An HTTP method.
 *
 * @param name the name of the method.
 */
abstract case class Method private (name: String) {
  override def toString = name

  def unapply(request: Request): Option[Path] =
    if (request.prelude.requestMethod.name == name)
      Some(Path(request.prelude.pathInfo) )
    else
      None

  def isSafe: Boolean
  def isIdempotent: Boolean

//  def :/(path: String): Path = new :/(this, Path(path))
//  def /(path: String): Path = new /(this, Path(path))
}

object Method {
  def notIdempotent(name: String): Method = new MethodImpl(name, false, false)
  def idempotent(name: String): Method = new MethodImpl(name, false, true)
  def safe(name: String): Method = new MethodImpl(name, true, true)

  private class MethodImpl(name: String, val isSafe: Boolean, val isIdempotent: Boolean) extends Method(name)

  private val registry: concurrent.Map[String, Method] = TrieMap.empty

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

  object Any {
    def unapply(request: RequestPrelude): Option[Path] = Some(Path(request.pathInfo))
  }

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