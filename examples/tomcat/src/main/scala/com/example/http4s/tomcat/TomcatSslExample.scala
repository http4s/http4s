package com.example.http4s
package tomcat

import org.http4s.server.ServerApp
import org.http4s.server.tomcat.TomcatConfig
import org.http4s.tls.TlsConfig

object TomcatSslExample extends ServerApp {
  val tlsConfig = TlsConfig.default
    .withKeyStore(".././keystore")
    .withKeyStorePassword("password")

  def server(args: List[String]) = TomcatConfig.default
    .bindHttps(8443, tlsConfig = tlsConfig)
    .mountService(ExampleService.service, "/http4s")
    .start()
}
