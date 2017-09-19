package org.http4s
package server
package tomcat

import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory

class TomcatServerSpec extends ServerAddressSpec {
  val builder = TomcatBuilder

  // Prevents us from loading jar and war URLs, but lets us
  // run Tomcat twice in the same JVM.  This makes me grumpy.
  TomcatURLStreamHandlerFactory.disable()
}
