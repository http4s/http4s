package org.http4s
package tomcat

import javax.servlet.http.HttpServlet
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import org.apache.catalina.startup.Tomcat
import org.http4s.servlet.{ServletContainer, ServletContainerBuilder}
import java.nio.file.Paths
import org.apache.catalina.{LifecycleState, Lifecycle, LifecycleEvent, LifecycleListener}
import java.util.concurrent.CountDownLatch

class TomcatServer private[tomcat] (tomcat: Tomcat) extends ServletContainer with LazyLogging {
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
        if (event.getLifecycle == Lifecycle.AFTER_STOP_EVENT)
          f
      }
    })
    this
  }
}

object TomcatServer {
  class Builder extends ServletContainerBuilder {
    type To = TomcatServer

    private val tomcat = new Tomcat
    tomcat.addContext("", getClass.getResource("/").getPath)
    timeout(Duration.Inf)

    override def withPort(port: Int): this.type = {
      tomcat.setPort(port)
      this
    }


    override def timeout(duration: Duration): this.type = {
      super.timeout(duration)
      val millis = new Integer(if (duration.isFinite) duration.toMillis.toInt else -1) // timeout == -1 == Inf
      tomcat.getConnector().setAttribute("connectionTimeout", millis)
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
