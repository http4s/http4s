package com.example.http4s
package war

import cats.effect.IO
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener
import org.http4s.servlet.syntax._
import scala.concurrent.ExecutionContext.Implicits.global

@WebListener
class Bootstrap extends ServletContextListener {

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val ctx = sce.getServletContext
    ctx.mountService("example", new ExampleService[IO].service)
    ()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {}
}
