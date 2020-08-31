/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.servlet

import javax.servlet.{AsyncEvent, AsyncListener}

protected[servlet] abstract class AbstractAsyncListener extends AsyncListener {
  override def onComplete(event: AsyncEvent): Unit = {}
  override def onError(event: AsyncEvent): Unit = {}
  override def onStartAsync(event: AsyncEvent): Unit = {}
  override def onTimeout(event: AsyncEvent): Unit = {}
}
