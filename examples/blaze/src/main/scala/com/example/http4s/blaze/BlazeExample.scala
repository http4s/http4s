package com.example.http4s.blaze

import com.example.http4s.ExampleService
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.ProcessApp

object BlazeExample extends ProcessApp {
  def process(args: List[String]) = BlazeBuilder.bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .serve
}
