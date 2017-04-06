package com.example.http4s
package blaze

import javax.net.ssl.SSLContext
import org.http4s.server.{Server, ServerApp}
import org.http4s.server.blaze.BlazeServerConfig
import scalaz.concurrent.Task

object BlazeSslExample extends ServerApp {
  // Not for production use.
  sys.props.getOrElseUpdate("javax.net.ssl.keyStore", "./keystore")
  sys.props.getOrElseUpdate("javax.net.ssl.keyStorePassword", "password")
  val sslContext = SSLContext.getDefault

  def server(args: List[String]): Task[Server] = BlazeServerConfig.default
    .withHttp2Enabled(true)
    .withSslContext(sslContext)
    .mountService(ExampleService.service, "/http4s")
    .bindHttp(8443)
    .start
}
