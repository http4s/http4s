package org.http4s.examples.cooldsl

import org.http4s.middleware.URITranslation
import org.http4s.examples.ExampleRoute
import org.http4s.blaze.pipeline.LeafBuilder
import java.nio.ByteBuffer
import org.http4s.blaze.Http1Stage
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory
import java.net.InetSocketAddress
import org.http4s.cooldsl.CoolService
import org.http4s.Header

/**
 * Created by Bryce Anderson on 5/9/14.
 */
class CoolDslExample(port: Int) {
  val route = URITranslation.translateRoot("/http4s")(new MyService).andThen{ t =>
    t.addHeaders(Header("Access-Control-Allow-Origin", "*"))
  }

  def f(): LeafBuilder[ByteBuffer] = new Http1Stage(route)

  private val factory = new SocketServerChannelFactory(f, 12, 8*1024)

  def run(): Unit = factory.bind(new InetSocketAddress(port)).run()
}

object CoolDslExample {
  println("Starting Http4s-blaze example")
  def main(args: Array[String]): Unit = new CoolDslExample(8080).run()
}
