package com.example.http4s
package war

import cats.effect.IO
import fs2.Scheduler
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener
import org.http4s.servlet.syntax._

@WebListener
class Bootstrap extends ServletContextListener {

  lazy val (scheduler, shutdownSched) = Scheduler.allocate[IO](corePoolSize = 2).unsafeRunSync()

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    implicit val scheduler: Scheduler = this.scheduler
    val ctx = sce.getServletContext
    ctx.mountService("example", new ExampleService[IO].service)
    ()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit =
    shutdownSched.unsafeRunSync()
}
