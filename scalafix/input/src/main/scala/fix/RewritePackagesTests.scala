/*
rule = v0_22
*/
package fix

import org.http4s.client.jetty._
import org.http4s.server.jetty._
import org.http4s.client.okhttp._
import org.http4s.server.tomcat._

object RewritePackagesTests {
  val tomcatBuilder = TomcatBuilder
  val tomcatBuilderQualified = org.http4s.server.tomcat.TomcatBuilder

  val jettyBuilder = JettyBuilder
  val jettyBuilderQualified = org.http4s.server.jetty.JettyBuilder

  val jettyClient = JettyClient
  val jettyClientQualified = org.http4s.client.jetty.JettyClient

  val okhttpBuilder = OkHttpBuilder
  val okhttpBuilderQualified = org.http4s.client.okhttp.OkHttpBuilder
}
