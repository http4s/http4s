package com.example.http4s

import org.http4s.server.ServerBuilder
import org.http4s.server.jetty.JettyBuilder
import org.http4s.server.tomcat.TomcatBuilder
import org.http4s.server.blaze.BlazeBuilder

import org.log4s.getLogger

/**
 * http4s' server builders let you run the same configuration on multiple backends.
 */
abstract class Example[B <: ServerBuilder] extends App {
  private val logger = getLogger

  // Extract common configuration into functions of ServerBuilder => ServerBuilder.
  // With type parameterization, it allows composition with backend-specific
  // extensions.
  def baseConfig(builder: B) = builder
    .bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")

  def builder: B

  val server = builder.run

  // Shut down the server with the process terminates.
  sys.addShutdownHook {
    logger.info(s"Shutting down server")
    server.shutdownNow()
  }

  server.awaitShutdown
}

object JettyExample extends Example[JettyBuilder] {
  def builder = baseConfig(JettyBuilder)
}

object TomcatExample extends Example[TomcatBuilder] {
  def builder = baseConfig(TomcatBuilder)
}

object BlazeExample extends Example[BlazeBuilder] {
  def builder = baseConfig(BlazeBuilder).withNio2(false)
}

