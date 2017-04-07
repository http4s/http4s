package com.example.http4s
package blaze

import javax.net.ssl.SSLContext
import org.http4s.server.{Server, ServerApp}
import org.http4s.server.blaze.BlazeServerConfig
import scalaz.concurrent.Task

/** Note that Java 8 is required to run this demo along with
  * loading the jetty ALPN TSL classes to the boot classpath.
  *
  * See http://eclipse.org/jetty/documentation/current/alpn-chapter.html
  * and the sbt build script of this project for ALPN details.
  *
  * Java 7 and earlier don't have a compatible set of TLS
  * cyphers that most clients will demand to use http2. If
  * clients drop the connection immediately, it might be
  * due to an "INADEQUATE_SECURITY" protocol error.
  *
  * https://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-9.2.1  *
  */
object BlazeHttp2Example extends ServerApp {
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
