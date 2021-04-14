/*
rule = v0_22
*/
package fix

import org.http4s.server.jetty._
import org.http4s.server.tomcat._

object RewritePackagesTests {
  val tomcatBuilder = TomcatBuilder
  val tomcatBuilderQualified = org.http4s.server.tomcat.TomcatBuilder

  val jettyBuilder = JettyBuilder
  val jettyBuilderQualified = org.http4s.server.jetty.JettyBuilder
}
