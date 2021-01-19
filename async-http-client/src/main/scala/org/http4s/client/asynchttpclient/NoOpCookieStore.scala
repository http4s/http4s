/*
 * Copyright 2016 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.client.asynchttpclient

import java.util.concurrent.atomic.AtomicInteger
import _root_.io.netty.handler.codec.http.cookie.Cookie
import org.asynchttpclient.cookie.CookieStore
import org.asynchttpclient.uri.Uri

private[asynchttpclient] class NoOpCookieStore extends CookieStore {
  private def empty: java.util.List[Cookie] = java.util.Collections.emptyList[Cookie]

  override def add(uri: Uri, cookie: Cookie): Unit = ()
  override def get(uri: Uri): java.util.List[Cookie] = empty
  override def getAll(): java.util.List[Cookie] = empty
  override def remove(pred: java.util.function.Predicate[Cookie]): Boolean = false
  override def clear(): Boolean = false
  override def evictExpired(): Unit = ()

  private val counter = new AtomicInteger(0)
  override def count(): Int = counter.get
  override def decrementAndGet(): Int = counter.decrementAndGet()
  override def incrementAndGet(): Int = counter.incrementAndGet()
}
