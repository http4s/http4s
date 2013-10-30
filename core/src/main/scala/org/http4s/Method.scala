package org.http4s

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

import Method._
import org.http4s.dsl.Path

/**
 * An HTTP method.
 *
 * @param name the name of the method.
 * @param methodType the safety guarantee of the method.  See RFC 2616, Section 9.1.1.
 * @param register true if the method should be registered
 */
sealed abstract class Method(val name: String, methodType: MethodType = MethodType.NotIdempotent, register: Boolean = false) {
  override def toString = name

  if (register)
    Method.registry(name) = this

  final def isSafe: Boolean = methodType == MethodType.Safe

  final def isIdempotent: Boolean = methodType != MethodType.NotIdempotent

//  def :/(path: String): Path = new :/(this, Path(path))
//  def /(path: String): Path = new /(this, Path(path))
}

/**
 * Denotes a method defined by the HTTP 1.1 specification.  Ensures that the
 * method is registered.
 *
 * @param name the name of the method.
 * @param methodType the safety guarantee of the method.  See RFC 2616, Section 9.1.1.
 */
sealed class StandardMethod(name: String, methodType: MethodType = MethodType.NotIdempotent)
  extends Method(name, methodType, true)

/**
 * Denotes an extension method allowed, but not defined, by the HTTP 1.1 specification.
 * These methods are not registered by default.
 *
 * @param name the name of the method.
 * @param methodType the safety guarantee of the method.  See RFC 2616, Section 9.1.1.
 */
class ExtensionMethod(name: String, methodType: MethodType = MethodType.NotIdempotent, register: Boolean = false)
  extends Method(name, methodType, register)

object Method {
  sealed trait MethodType

  object MethodType {
    case object NotIdempotent extends MethodType
    case object Idempotent extends MethodType
    case object Safe extends MethodType
  }

  import MethodType._
  object Options extends StandardMethod("OPTIONS", Idempotent)
  object Get     extends StandardMethod("GET",     Safe)
  object Head    extends StandardMethod("HEAD",    Safe)
  object Post    extends StandardMethod("POST",    NotIdempotent)
  object Put     extends StandardMethod("PUT",     Idempotent)
  object Delete  extends StandardMethod("DELETE",  Idempotent)
  object Trace   extends StandardMethod("TRACE",   Idempotent)
  object Connect extends StandardMethod("CONNECT", NotIdempotent)
  // http://tools.ietf.org/html/rfc5789
  object Patch   extends ExtensionMethod("PATCH", NotIdempotent, register = true)

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
  def apply(name: String): Method = get(name).getOrElse(new ExtensionMethod(name, NotIdempotent))
}