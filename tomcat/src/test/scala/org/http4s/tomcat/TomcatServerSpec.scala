package org.http4s
package server
package tomcat

class TomcatServerSpec extends ServerAddressSpec {
  def serverOnPort0 = TomcatConfig.default
    .bindHttp(0)
    .start
}
