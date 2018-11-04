package com.example.http4s
package war

import cats.effect.{ExitCode, IO, IOApp}
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener
import org.http4s.servlet.syntax._

@WebListener
class Bootstrap extends ServletContextListener with IOApp {

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val ctx = sce.getServletContext
    ctx.mountService("example", new ExampleService[IO].routes)
    ()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {}

  override def run(args: List[String]): IO[ExitCode] = ???
}
