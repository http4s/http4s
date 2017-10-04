package org.http4s
package server
package tomcat

import cats.effect.IO
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory

class TomcatServerSpec extends {
  // Prevents us from loading jar and war URLs, but lets us
  // run Tomcat twice in the same JVM.  This makes me grumpy.
  //
  // Needs to run before the server is initialized in the superclass.
  // This also makes me grumpy.
  val _ = TomcatURLStreamHandlerFactory.disable()
} with ServerSpec {
  def builder = TomcatBuilder[IO]
}
