package com.example.http4s

import org.http4s.jetty.{JettyBuilder, JettyServer}
import org.http4s.server.ServerBuilder
import org.http4s.server.blaze.{BlazeBuilder, BlazeServer}
import org.http4s.tomcat.{TomcatBuilder, TomcatServer}

import org.log4s.getLogger

/**
 * http4s' server builders let you run the same configuration on multiple backends.
 */
abstract class Example[B <: ServerBuilder[B]] extends App {
  private[this] val logger = getLogger

  // Extract common configuration into functions of ServerBuilder => ServerBuilder.
  // With type parameterization, it allows composition with backend-specific
  // extensions.
  def baseConfig(builder: B): B = builder
    .withHost("127.0.0.1")
    .withPort(8080)
    .mountService(ExampleService.service, "/http4s")

  def builder: B

  val server = builder.run

  // Shut down the server with the process terminates.
  sys.addShutdownHook {
    logger.info(s"Shutting down server")
    server.shutdownNow()
  }

  // Block until shutdown.
  synchronized { wait() }
}

object JettyExample extends Example[JettyBuilder] {
  def builder = baseConfig(JettyServer)
}

object TomcatExample extends Example[TomcatBuilder] {
  def builder = baseConfig(TomcatServer)
}

object BlazeExample extends Example[BlazeBuilder] {
  def builder = baseConfig(BlazeServer).withNio2(false)
}

