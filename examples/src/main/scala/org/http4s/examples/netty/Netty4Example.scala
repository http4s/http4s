
package org.http4s
package examples
package netty

import org.http4s._
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s.util.middleware.{GZip, URITranslation,ChunkAggregator}
import org.http4s.netty.http.SimpleNettyServer


object Netty4Example extends App with Logging {
  val route = ChunkAggregator(GZip(new ExampleRoute().apply()))
  //val route = new ExampleRoute().apply()
  val server = SimpleNettyServer()(URITranslation.translateRoot("/http4s")(route))
  server.run()
}

