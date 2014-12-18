package com.example.http4s
package servlet

/// code_ref: servlet_example
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.tomcat.TomcatBuilder
import org.http4s.servlet.ServletContainer

class ServletExample extends App {
  def go(builder: ServletContainer): Unit = builder
    .bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new LegacyServlet, "/legacy/*")
    .run
    .awaitShutdown()
}

object TomcatExample extends ServletExample {
  go(TomcatBuilder)
}

object JettyExample extends ServletExample {
  go(JettyBuilder)
}
/// end_code_ref
