/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
