package com.example.http4s.blaze

import java.util.concurrent._
import scala.concurrent.duration._
import scalaz.concurrent._
import com.example.http4s.ExampleService
import org.http4s.server.ServerApp
import org.http4s.server.blaze.BlazeBuilder

object AwaitShutdownExample extends App {
  val server = BlazeBuilder.bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .start
    .run

  // Some out of band process that shuts down the server
  server.shutdown.after(1.second).runAsync { _ => }

  server.awaitShutdown()

  // `Task.after` before 7.3 starts a thread inside here
  Timer.default.stop()
}
