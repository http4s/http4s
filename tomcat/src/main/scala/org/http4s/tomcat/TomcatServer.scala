package org.http4s
package tomcat

import javax.servlet.http.HttpServlet
import org.http4s.server.{HasIdleTimeout, HasConnectionTimeout}
import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import org.apache.catalina.startup.Tomcat
import org.http4s.servlet.{ServletContainer, ServletContainerBuilder}
import java.nio.file.Paths
import org.apache.catalina.{LifecycleState, Lifecycle, LifecycleEvent, LifecycleListener}
import java.util.concurrent.CountDownLatch

class TomcatServer private[tomcat] (tomcat: Tomcat) extends ServletContainer {
  def start: Task[this.type] = Task.delay {
    tomcat.start()
    this
  }

  def shutdown: Task[this.type] = Task.delay {
    tomcat.stop()
    this
  }

  def join(): this.type = {
    if (tomcat.getServer.getState.ordinal < LifecycleState.STOPPED.ordinal) {
      val latch = new CountDownLatch(1)
      onShutdown { latch.countDown() }
      latch.await()
    }
    this
  }

  override def onShutdown(f: => Unit): this.type = {
    tomcat.getServer.addLifecycleListener(new LifecycleListener {
      override def lifecycleEvent(event: LifecycleEvent): Unit = {
        if (Lifecycle.AFTER_STOP_EVENT.equals(event.getLifecycle))
          f
      }
    })
    this
  }
}

object TomcatServer {
  class Builder extends ServletContainerBuilder with HasConnectionTimeout with HasIdleTimeout {
    type To = TomcatServer

    private val tomcat = new Tomcat
    tomcat.addContext("", getClass.getResource("/").getPath)
    withConnectionTimeout(Duration.Inf)
    withIdleTimeout(Duration.Inf)

    override def withPort(port: Int): this.type = {
      tomcat.setPort(port)
      this
    }

    override def withHost(host: String): this.type = {
      tomcat.getConnector.setAttribute("address", host)
      this
    }

    /** Add timeout for idle connections
      * '''WARNING:''' Tomcat maintains connections on a fixed interval determined by the global
      * attribute worker.maintain with a default interval of 60 seconds. In the worst case the connection
      * may not timeout for an additional 59.999 seconds from the specified Duration
      * @param timeout Duration to wait for an idle connection before timing out
      * @return this [[server.ServerBuilder]]
      */
    override def withIdleTimeout(timeout: Duration): this.type = {
      // the connection_pool_timeout attribute has units of seconds
      val millis = new Integer(if (timeout.isFinite) timeout.toSeconds.toInt else 0)
      tomcat.getConnector.setAttribute("connection_pool_timeout", millis)
      this
    }

    def withConnectionTimeout(duration: Duration): this.type = {
      val millis = new Integer(if (duration.isFinite) duration.toMillis.toInt else -1) // timeout == -1 == Inf
      tomcat.getConnector.setAttribute("connectionTimeout", millis)
      this
    }

    def build: To = new TomcatServer(tomcat)

    def mountServlet(servlet: HttpServlet, urlMapping: String): this.type = {
      val wrapper = tomcat.addServlet("", defaultServletName(servlet), servlet)
      wrapper.addMapping(urlMapping)
      wrapper.setAsyncSupported(true)
      this
    }
  }

  def newBuilder: Builder = new Builder
}
