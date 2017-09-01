/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/ObjectRegistry.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s.util

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap
import string._

private[http4s] trait Registry {
  type Key
  type Value <: AnyRef

  protected val registry: concurrent.TrieMap[Key, Value] = TrieMap.empty

  // TODO: For testing purposes
  private[http4s] def snapshot: TrieMap[Key, Value] = registry.snapshot()

  def get(key: Key): Option[Value] = registry.get(key)

  def getOrElse[V2 >: Value](key: Key, default: => V2): V2 = registry.getOrElse(key, default)

  def getOrElseCreate(key: Key)(implicit ev: Key => Value): Value = getOrElse(key, ev(key))

  protected def getOrElseUpdate(key: Key, default: => Value): Value =
    registry.getOrElseUpdate(key, default)

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
