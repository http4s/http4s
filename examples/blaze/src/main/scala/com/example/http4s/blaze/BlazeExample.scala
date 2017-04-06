package com.example.http4s.blaze

import com.example.http4s.ExampleService
import org.http4s.server.ServerApp
import org.http4s.server.blaze.BlazeServerConfig

object BlazeExample extends ServerApp {
  def server(args: List[String]) = BlazeServerConfig.default
    .bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .start
}
