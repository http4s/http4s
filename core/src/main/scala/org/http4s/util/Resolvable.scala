package org.http4s.util

private[http4s] trait Resolvable[K, V <: AnyRef] extends Registry[K, V] {
  protected def stringToRegistryKey(s: String): K

  protected def fromKey(k: K): V

  def resolve(s: String): V = {
    val key = stringToRegistryKey(s)
    lookupOrElse(key, fromKey(key))
  }
}


