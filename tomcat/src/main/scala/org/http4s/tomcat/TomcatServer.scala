package org.http4s
package tomcat

import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServlet
import org.http4s.server._
import org.http4s.servlet.Http4sServlet

import scala.concurrent.duration.Duration
import scalaz.concurrent.{Strategy, Task}
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.{LifecycleState, Lifecycle, LifecycleEvent, LifecycleListener}
import java.util.concurrent.CountDownLatch

case class TomcatServer private[tomcat] (tomcat: Tomcat) extends Server {
  def start: Task[this.type] = Task.delay {
    tomcat.start()
    this
  }

  def shutdown: Task[this.type] = Task.delay {
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

object TomcatServer extends ServerBackend {
  /**
   * Returns a Task to start a Tomcat server.
   *
   * idleTimeout: Tomcat maintains connections on a fixed interval determined by the global
   * attribute worker.maintain with a default interval of 60 seconds. In the worst case the connection
   * may not timeout for an additional 59.999 seconds from the specified Duration
   */
  def apply(config: ServerConfig): Task[Server] = Task.delay {
    val tomcat = new Tomcat

    tomcat.addContext("", getClass.getResource("/").getPath)
    tomcat.getConnector.setAttribute("address", config.host)
    tomcat.setPort(config.port)

    val idleTimeout = config.idleTimeout
    tomcat.getConnector.setAttribute("connection_pool_timeout",
      if (idleTimeout.isFinite) idleTimeout.toSeconds.toInt else 0)

    val nameCounter = new AtomicInteger
    for (serviceMount <- config.serviceMounts) {
      val servlet = new Http4sServlet(
        service = serviceMount.service,
        threadPool = config.executor
      )
      val wrapper = tomcat.addServlet("", s"servlet-${nameCounter.getAndIncrement}", servlet)
      wrapper.addMapping(s"${serviceMount.prefix}/*")
      wrapper.setAsyncSupported(true)
    }

    tomcat.start()

    new Server {
      override def shutdown: Task[this.type] = Task.delay {
        tomcat.stop()
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
  }
}
