package com.example.http4s.tomcat

import com.example.http4s.ExampleService
import org.http4s.server.tomcat.TomcatBuilder

object TomcatExample extends App {
  TomcatBuilder
    .bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .run
    .awaitShutdown()
}
