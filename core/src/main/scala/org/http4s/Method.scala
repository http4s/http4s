package org.http4s

import java.util.Locale
import scala.collection
import collection.concurrent.TrieMap
import annotation.tailrec

/**
 * An HTTP method.
 *
 * @param name the name of the method.
 * @param isSafe true if the method is safe.  See RFC 2616, Section 9.1.1.
 * @param isIdempotent true if the method is idempotent.  See RFC 2616, Section 9.1.2.
 * @param register true if the method should be registered
 */
sealed abstract class Method(val name: String, val isSafe: Boolean, val isIdempotent: Boolean, register: Boolean = false) {
  override def toString = name

  if (register)
    Method.register(this)
}

/**
 * Denotes a method defined by the HTTP 1.1 specification.  Ensures that the
 * method is registered.
 *
 * @param name the name of the method.
 * @param isSafe true if the method is safe.  See RFC 2616, Section 9.1.1.
 * @param isIdempotent true if the method is idempotent.  See RFC 2616, Section 9.1.2.
 */
sealed class StandardMethod(name: String, isSafe: Boolean, isIdempotent: Boolean)
  extends Method(name, isSafe, isIdempotent, true)

/**
 * Denotes an extension method allowed, but not defined, by the HTTP 1.1 specification.
 * These methods are not registered by default.
 *
 * @param name the name of the method.
 * @param isSafe true if the method is safe.  See RFC 2616, Section 9.1.1.
 * @param isIdempotent true if the method is idempotent.  See RFC 2616, Section 9.1.2.
 */
class ExtensionMethod(name: String, isSafe: Boolean, isIdempotent: Boolean, register: Boolean = false)
  extends Method(name, isSafe, isIdempotent, register)

object Method {
  val Options = new StandardMethod("OPTIONS", isSafe = false, isIdempotent = true)
  val Get     = new StandardMethod("GET",     isSafe = true,  isIdempotent = true)
  val Head    = new StandardMethod("HEAD",    isSafe = true,  isIdempotent = true)
  val Post    = new StandardMethod("POST",    isSafe = false, isIdempotent = false)
  val Put     = new StandardMethod("PUT",     isSafe = false, isIdempotent = true)
  val Delete  = new StandardMethod("DELETE",  isSafe = false, isIdempotent = true)
  val Trace   = new StandardMethod("TRACE",   isSafe = false, isIdempotent = true)
  val Connect = new StandardMethod("CONNECT", isSafe = true,  isIdempotent = false)

  // PATCH is not part of the RFC, but common enough we'll support it.
  val Patch   = new ExtensionMethod("PATCH",  isSafe = false, isIdempotent = false, register = true)

  private[this] lazy val registry: collection.concurrent.Map[String, Method] = TrieMap.empty

  @tailrec
  private def register(method: Method) {
    val oldValue = registry.putIfAbsent(method.name, method)
    if (oldValue.isDefined && !registry.replace(method.name, oldValue.get, method))
      register(method)
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
  def get(name: String): Option[Method] = {
    val canonicalName = name.toUpperCase(Locale.ENGLISH)
    registry.get(canonicalName)
  }

  /**
   * Retrieves a method from the registry, or creates an extension method otherwise.
   * The extension method is not registered, and assumed to be neither safe nor
   * idempotent.
   *
   * @param name the name, case insensitive
   * @return the method, if registered; otherwise, an extension method
   */
  def apply(name: String): Method = get(name).getOrElse(new ExtensionMethod(name, isSafe = false, isIdempotent = false))


}