package org.http4s.examples
package servlet

import org.http4s.jetty.JettyServer

object JettyExample extends App {
  JettyServer.newBuilder
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new RawServlet, "/raw/*")
    .run()
    .join()
}
