package org.http4s.examples.blaze


import org.http4s.server.blaze.BlazeServer
import org.http4s.examples.ExampleService

object BlazeExample extends App {
  println("Starting Http4s-blaze example")
  BlazeServer.newBuilder
    .mountService(ExampleService.service, "/http4s")
    .run()
}
