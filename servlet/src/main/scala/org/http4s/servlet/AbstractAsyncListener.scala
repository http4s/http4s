package org.http4s.servlet

import javax.servlet.{AsyncEvent, AsyncListener}

protected[servlet] abstract class AbstractAsyncListener extends AsyncListener {
  override def onComplete(event: AsyncEvent): Unit = {}
  override def onError(event: AsyncEvent): Unit = {}
  override def onStartAsync(event: AsyncEvent): Unit = {}
  override def onTimeout(event: AsyncEvent): Unit = {}
}
