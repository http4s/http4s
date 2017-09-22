package org.http4s
package server
package tomcat

import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory
import org.specs2.specification.Before

class TomcatServerSpec extends ServerSpec with Before {
  def before =
    // Prevents us from loading jar and war URLs, but lets us
    // run Tomcat twice in the same JVM.  This makes me grumpy.
    //
    // Needs to run before the server is initialized in the superclass.
    // This also makes me grumpy.
    TomcatURLStreamHandlerFactory.disable()

  def builder = TomcatBuilder
}
