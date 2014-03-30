package org.http4s.examples.blaze


/**
* @author Bryce Anderson
*         Created on 3/26/14.
*/

import org.http4s.blaze.Http4sStage
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory
import org.http4s.blaze.pipeline.LeafBuilder

import java.nio.ByteBuffer
import java.net.InetSocketAddress

import org.http4s.util.middleware.URITranslation
import org.http4s.examples.ExampleRoute

/**
* @author Bryce Anderson
*         Created on 1/10/14
*/
class BlazeExample(port: Int) {

  val route = new ExampleRoute().apply()

  def f(): LeafBuilder[ByteBuffer] = new Http4sStage(URITranslation.translateRoot("/http4s")(route))

  private val factory = new SocketServerChannelFactory(f, 12, 8*1024)

  def run(): Unit = factory.bind(new InetSocketAddress(port)).run()
}

object BlazeExample {
  println("Starting Http4s-blaze example")
  def main(args: Array[String]): Unit = new BlazeExample(8080).run()
}
