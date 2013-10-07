
package org.http4s
package netty

import org.http4s._
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s.util.middleware.URITranslation
import scalaz.concurrent.Task

object Netty4Example extends App with Logging {

  //SimpleNettyServer()(URITranslation.translateRoot("/http4s")(new ExampleRoute[Task].apply())
  SimpleNettyServer()(new ExampleRoute().apply())

}

