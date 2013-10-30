package org.http4s

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

import Method._

/**
 * An HTTP method.
 *
 * @param name the name of the method.
 * @param methodType the safety guarantee of the method.  See RFC 2616, Section 9.1.1.
 * @param register true if the method should be registered
 */
class Method(val name: String, methodType: MethodType = MethodType.NotIdempotent, register: Boolean = false) {
  override def toString = name

  if (register)
    Method.registry(name) = this

  def unapply(request: RequestPrelude): Option[Path] =
    if (request.requestMethod.name == name) Some(Path(request.pathInfo) ) else None

  final def isSafe: Boolean = methodType == MethodType.Safe

  final def isIdempotent: Boolean = methodType != MethodType.NotIdempotent

//  def :/(path: String): Path = new :/(this, Path(path))
//  def /(path: String): Path = new /(this, Path(path))
}

object Method {
  sealed trait MethodType

  object MethodType {
    case object NotIdempotent extends MethodType
    case object Idempotent extends MethodType
    case object Safe extends MethodType
  }

  import MethodType._
  object Options extends Method("OPTIONS", Idempotent   , true)
  object Get     extends Method("GET",     Safe         , true)
  object Head    extends Method("HEAD",    Safe         , true)
  object Post    extends Method("POST",    NotIdempotent, true)
  object Put     extends Method("PUT",     Idempotent   , true)
  object Delete  extends Method("DELETE",  Idempotent   , true)
  object Trace   extends Method("TRACE",   Idempotent   , true)
  object Connect extends Method("CONNECT", NotIdempotent, true)
  // http://tools.ietf.org/html/rfc5789
  object Patch   extends Method("PATCH",   NotIdempotent, true)

  object Any {
    def unapply(request: RequestPrelude): Option[Path] = Some(Path(request.pathInfo))
  }

  private val registry: concurrent.Map[String, Method] = TrieMap.empty

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
  def apply(name: String): Method = get(name).getOrElse(new Method(name, NotIdempotent))
}