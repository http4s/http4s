package org.http4s
package examples.blaze

import org.http4s.blaze.pipeline.LeafBuilder
import java.nio.ByteBuffer
import org.http4s.blaze.Http1Stage
import org.http4s.util.middleware.URITranslation
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory
import java.net.InetSocketAddress

import org.http4s.Status._
import org.http4s.blaze.websocket.WebSocketSupport


/**
 * Created by Bryce Anderson on 3/30/14.
 */
class BlazeWebSocketExample(port: Int) {

  import dsl._
  import websocket._
  import scala.concurrent.duration._
  import scalaz.stream.Process
  import scalaz.concurrent.Task

  val route: HttpService = {
    case Get -> Root / "hello" =>
      Ok("Hello world.")

    case req@ Get -> Root / "ws" =>
      println("Running websocket.")
      val src = Process.awakeEvery(1.seconds).map{ d => "Ping! " + d }
      val sink = Process.constant{c: BodyChunk => Task.delay( println(c.decodeString(CharacterSet.`UTF-8`)))}
      WS(src, sink)

  }

  def f(): LeafBuilder[ByteBuffer] = new Http1Stage(URITranslation.translateRoot("/http4s")(route)) with WebSocketSupport

  private val factory = new SocketServerChannelFactory(f, 12, 8*1024)

  def run(): Unit = factory.bind(new InetSocketAddress(port)).run()

}

object BlazeWebSocketExample {
  def main(args: Array[String]): Unit = new BlazeWebSocketExample(8080).run()
}
