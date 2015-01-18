package com.example.http4s
package jetty

import org.http4s.server.jetty.JettyBuilder

/// code_ref: jetty_example
object JettyExample extends App {
  JettyBuilder
    .bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .run
    .awaitShutdown()
}
/// end_code_ref
