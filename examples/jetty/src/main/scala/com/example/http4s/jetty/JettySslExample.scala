package com.example.http4s
package jetty

import javax.net.ssl.SSLContext
import org.http4s.server.ServerApp
import org.http4s.server.jetty.JettyConfig

object JettySslExample extends ServerApp {
  // Not for production use.
  sys.props.getOrElseUpdate("javax.net.ssl.keyStore", "./keystore")
  sys.props.getOrElseUpdate("javax.net.ssl.keyStorePassword", "password")
  val sslContext = SSLContext.getDefault

  def server(args: List[String]) = JettyConfig.default
    .bindHttps(8443)
    .mountService(ExampleService.service, "/http4s")
    .start
}
