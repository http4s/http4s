package com.examples.http4s.war

import cats.effect.IO
import com.example.http4s.ExampleService
import fs2.Scheduler
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}
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
