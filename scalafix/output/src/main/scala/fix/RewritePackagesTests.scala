package fix

import org.http4s.asynchttpclient.client._
import org.http4s.blaze.client._
import org.http4s.blaze.server._
import org.http4s.jetty.client._
import org.http4s.jetty.server._
import org.http4s.okhttp.client._
import org.http4s.tomcat.server._

object RewritePackagesTests {
  val tomcatBuilder = TomcatBuilder
  val tomcatBuilderQualified = org.http4s.tomcat.server.TomcatBuilder

  val jettyBuilder = JettyBuilder
  val jettyBuilderQualified = org.http4s.jetty.server.JettyBuilder

  val jettyClient = JettyClient
  val jettyClientQualified = org.http4s.jetty.client.JettyClient

  val okhttpBuilder = OkHttpBuilder
  val okhttpBuilderQualified = org.http4s.okhttp.client.OkHttpBuilder

  val asyncHttpClient = AsyncHttpClient
  val asyncHttpClientQualified = org.http4s.asynchttpclient.client.AsyncHttpClient

  val blazeClient = BlazeClient
  val blazeClientQualified = org.http4s.blaze.client.BlazeClient

  val blazeBuilder = BlazeServerBuilder
  val blazeBuilderQualified = org.http4s.blaze.server.BlazeServerBuilder
}
