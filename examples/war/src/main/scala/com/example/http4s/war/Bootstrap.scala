package com.example.http4s
package war

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}
import org.http4s.servlet.syntax._
import scala.concurrent.ExecutionContext

@WebListener
class Bootstrap extends ServletContextListener with IOApp {
  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val ctx = sce.getServletContext
    val blocker = Blocker.liftExecutionContext(ExecutionContext.global)
    ctx.mountService("example", ExampleService[IO](blocker).routes)
    ()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {}

  override def run(args: List[String]): IO[ExitCode] = ???
}
