package org.http4s
package tomcat

import javax.servlet.http.HttpServlet
import com.typesafe.scalalogging.slf4j.LazyLogging
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
    println("STOPPING TOMCAT")
    tomcat.stop()
    this
  }

  def join(): this.type = {
    if (tomcat.getServer.getState.ordinal < LifecycleState.STOPPED.ordinal) {
      val latch = new CountDownLatch(1)
      tomcat.getServer.addLifecycleListener(new LifecycleListener {
        override def lifecycleEvent(event: LifecycleEvent): Unit = {
          if (event.getLifecycle == Lifecycle.AFTER_STOP_EVENT)
            latch.countDown()
        }
      })
      latch.await()
    }
    this
  }
}

object TomcatServer {
  class Builder extends ServletContainerBuilder {
    type To = TomcatServer

    private val tomcat = new Tomcat
    tomcat.addContext("", getClass.getResource("/").getPath)

    override def withPort(port: Int): this.type = {
      tomcat.setPort(port)
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
