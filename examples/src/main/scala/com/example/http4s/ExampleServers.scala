package com.example.http4s

import org.http4s.jetty.JettyServer
import org.http4s.server.blaze.BlazeServer
import org.http4s.server.{ServerBackend, ServerConfig}
import org.http4s.tomcat.TomcatServer

import org.log4s.getLogger

/**
 * http4s' server builders let you run the same configuration on multiple backends.
 */
trait Example extends App {
  private[this] val logger = getLogger

  def config = ServerConfig
    .withHost("127.0.0.1")
    .withPort(8080)
    .mountService(ExampleService.service, "/http4s")

  def backend: ServerBackend

  // This will run the the service on the supplied backend
  val server = backend(config).run
  logger.info(s"Service started at http://${config.host}:${config.port}/http4s/")

  // Shut down the server with the process terminates.
  sys.addShutdownHook {
    logger.info(s"Shutting down server")
    server.shutdownNow()
  }

  // Block until shutdown.
  synchronized { wait() }
}

object JettyExample extends Example {
  override def backend = JettyServer
}

object TomcatExample extends Example {
  override def backend = TomcatServer
}

object BlazeExample extends Example {
  import BlazeServer._

  override def config = super.config
    // Server configurations are extensible with arbitrary keys.  Use syntax
    // to hide the gory details.
    .withNio2(true)

  override def backend = BlazeServer
}
