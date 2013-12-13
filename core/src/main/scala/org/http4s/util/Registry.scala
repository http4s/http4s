package org.http4s.util

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

private[http4s] trait Registry[K, V <: AnyRef] {
  protected val registry: concurrent.Map[K, V] = TrieMap.empty

  final def register(key: K, obj: V): obj.type = {
    registry.update(key, obj)
    obj
  }

  def getForKey(key: K): Option[V] = registry.get(key)

  protected def getOrElse(key: K, default: =>V): V = getForKey(key).getOrElse(default)
}

