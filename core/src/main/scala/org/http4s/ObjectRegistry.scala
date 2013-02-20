package org.http4s


private[http4s] trait ObjectRegistry[K, V] {
  private[this] var registry = Map.empty[K, V]
  private[this] val lock: AnyRef = new Object


  final def register(key: K, obj: V) = lock.synchronized {
    registry = registry.updated(key, obj)
  }

  def getForKey(key: K): Option[V] = registry.get(key)
}

