package com.example.http4s
package servlet

import com.example.http4s.ExampleService
import org.http4s.tomcat.TomcatServer

object TomcatExample extends App {
  val tomcat = TomcatServer.newBuilder
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new RawServlet, "/raw/*")
    .run()
    .join()
}

