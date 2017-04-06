package org.http4s
package server
package jetty

class JettyServerSpec extends ServerAddressSpec {
  val serverOnPort0 = JettyConfig.default
    .bindHttp(0)
    .start
}
