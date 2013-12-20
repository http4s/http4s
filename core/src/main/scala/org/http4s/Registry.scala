package org.http4s

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

private[http4s] trait Registry[K, V <: AnyRef] {
  protected val registry: concurrent.Map[K, V] = TrieMap.empty

  protected def register(key: K, obj: V): obj.type = {
    registry.update(key, obj)
    obj
  }

  protected def lookup(key: K): Option[V] = registry.get(key)

  protected def lookupOrElse(key: K, default: => V): V = lookup(key).getOrElse(default)
}
