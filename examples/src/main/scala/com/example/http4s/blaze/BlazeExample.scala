package com.example.http4s
package blaze

import org.http4s.server.blaze.BlazeServer

object BlazeExample extends App {
  println("Starting Http4s-blaze example")
  BlazeServer.newBuilder
    .withHost("0.0.0.0")
    .mountService(ExampleService.service, "/http4s")
    .run()
}
