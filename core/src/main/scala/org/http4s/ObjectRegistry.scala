package org.http4s

import java.util.concurrent.atomic.AtomicReference


private[http4s] trait ObjectRegistry[K, V] {
  private[this] val registry = new AtomicReference(Map.empty[K, V])

  final def register(key: K, obj: V) =
    registry.set(registry.get.updated(key, obj))


  def getForKey(key: K): Option[V] = registry.get.get(key)
}

