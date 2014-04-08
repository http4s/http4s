package org.http4s.util

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap
import string._

private[http4s] trait Registry {
  type Key
  type Value <: AnyRef

  protected val registry: concurrent.TrieMap[Key, Value] = TrieMap.empty

  // TODO: For testing purposes
  private[http4s] def snapshot: TrieMap[Key,Value] = registry.snapshot()

  def get(key: Key): Option[Value] = registry.get(key)

  def getOrElse[V2 >: Value](key: Key, default: => V2): V2 = registry.getOrElse(key, default)

  def getOrElseCreate(key: Key)(implicit ev: Key => Value): Value = getOrElse(key, ev(key))

  protected def getOrElseUpdate(key: Key, default: => Value): Value = registry.getOrElseUpdate(key, default)

  protected def register(key: Key, value: Value): value.type = {
    registry.update(key, value)
    value
  }

  protected def registerKey(key: Key)(implicit ev: Key => Value): Value =
    register(key, ev(key))

  protected def registerValue(value: Value)(implicit ev: Value => Key): value.type = {
    register(ev(value), value)
    value
  }
}
