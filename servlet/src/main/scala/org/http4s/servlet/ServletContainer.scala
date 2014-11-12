package org.http4s
package servlet

import java.util.concurrent.ExecutorService

import org.http4s.server._

import javax.servlet.http.HttpServlet

import scala.concurrent.duration.Duration
import scalaz.concurrent.Strategy

trait ServletContainer extends Server

trait ServletContainerBuilder extends ServerBuilder with HasAsyncTimeout {
  type To <: ServletContainer

  private var asyncTimeout: Duration = Duration.Inf

  private var _threadPool: ExecutorService = Strategy.DefaultExecutorService

  protected def defaultServletName(servlet: HttpServlet): String =
    s"${servlet.getClass.getName}-${System.identityHashCode(servlet)}"

  def mountService(service: HttpService, prefix: String): this.type = {
    val pathMapping = s"${prefix}/*"
    mountServlet(new Http4sServlet(service, asyncTimeout, threadPool = _threadPool), pathMapping)
  }

  def mountServlet(servlet: HttpServlet, urlMapping: String): this.type

  override def withAsyncTimeout(asyncTimeout: Duration): this.type = {
    this.asyncTimeout = asyncTimeout
    this
  }

  override def withThreadPool(pool: ExecutorService): this.type = {
    this._threadPool = pool
    this
  }
}
