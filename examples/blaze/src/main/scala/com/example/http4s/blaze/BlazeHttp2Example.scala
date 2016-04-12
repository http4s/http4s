package com.example.http4s
package blaze

import com.example.http4s.ssl.SslExample
import org.http4s.server.blaze.BlazeBuilder

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
object BlazeHttp2Example extends SslExample {
  def builder = BlazeBuilder.enableHttp2(true)
}
