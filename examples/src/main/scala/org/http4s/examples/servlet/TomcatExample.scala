package org.http4s.examples
package servlet

import org.http4s.tomcat.TomcatServer

object TomcatExample extends App {
  val tomcat = TomcatServer.newBuilder
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new RawServlet, "/raw/*")
    .run()
    .join()
}

