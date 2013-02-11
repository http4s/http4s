package org.http4s

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec


private[http4s] trait ObjectRegistry[K, V] {
  private[this] val registry = new AtomicReference(Map.empty[K, V])

  @tailrec
  final def register(key: K, obj: V) {
    val current = registry.get
    val updated = current.updated(key, obj)
    if (!registry.compareAndSet(current, updated))
      register(key, obj)
  }

  def getForKey(key: K): Option[V] = registry.get.get(key)
}

