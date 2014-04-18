package org.http4s.examples
package blaze

import org.http4s._
import org.http4s.Status._
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.Http1Stage
import org.http4s.middleware.URITranslation
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory
import org.http4s.blaze.websocket.WebSocketSupport

import java.nio.ByteBuffer
import java.net.InetSocketAddress


/**
 * Created by Bryce Anderson on 3/30/14.
 */
object BlazeWebSocketExample extends App {

  import dsl._
  import websocket._
  import scala.concurrent.duration._
  import scalaz.stream.Process
  import Process.Sink
  import scalaz.concurrent.Task
  import scalaz.stream.async.topic


  val route: HttpService = {
    case Get -> Root / "hello" =>
      Ok("Hello world.")

    case req@ Get -> Root / "ws" =>
      val src = Process.awakeEvery(1.seconds).map{ d => Text(s"Ping! $d") }
      val sink: Sink[Task, WSFrame] = Process.constant {
        case Text(t) => Task.delay( println(t))
        case f       => Task.delay(println(s"Unknown type: $f"))
      }
      WS(src, sink)

    case req@ Get -> Root / "wsecho" =>
      val t = topic[WSFrame]()
      val src = t.subscribe.collect {
        case Text(msg) => Text("You sent the server: " + msg)
      }

      WS(src, t.publish)
  }

  def pipebuilder(): LeafBuilder[ByteBuffer] =
    new Http1Stage(URITranslation.translateRoot("/http4s")(route)) with WebSocketSupport

  new SocketServerChannelFactory(pipebuilder, 12, 8*1024)
    .bind(new InetSocketAddress(8080))
    .run()
}
