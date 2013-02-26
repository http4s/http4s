package org.http4s
package grizzly

import attributes.ServerContext

/**
 * @author Bryce Anderson
 * @author ross
 */

object GrizzlyExample extends App {

  implicit val serverContext: ServerContext = new ServerContext
  SimpleGrizzlyServer(serverRoot = "/http4s/*")(ExampleRoute())

}
