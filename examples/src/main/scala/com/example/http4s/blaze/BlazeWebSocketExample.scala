package com.example.http4s
package blaze

import scalaz.concurrent.Strategy

import org.http4s._
import org.http4s.websocket.WebsocketBits._
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.server.HttpService
import org.http4s.server.blaze.{WebSocketSupport, Http1ServerStage}
import org.http4s.server.middleware.URITranslation
import org.http4s.blaze.channel.nio1.SocketServerChannelFactory

import java.nio.ByteBuffer
import java.net.InetSocketAddress
import org.http4s.blaze.channel.SocketConnection
import scalaz.stream.DefaultScheduler


object BlazeWebSocketExample extends App {

  import dsl._
  import org.http4s.server.websocket._
  import scala.concurrent.duration._
  import scalaz.stream.{Process, Sink}
  import scalaz.concurrent.Task
  import scalaz.stream.async.topic


  val route = HttpService {
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case req@ GET -> Root / "ws" =>
      val src = Process.awakeEvery(1.seconds)(Strategy.DefaultStrategy, DefaultScheduler).map{ d => Text(s"Ping! $d") }
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.delay( println(t))
        case f       => Task.delay(println(s"Unknown type: $f"))
      }
      WS(src, sink)

    case req@ GET -> Root / "wsecho" =>
      val t = topic[WebSocketFrame]()
      val src = t.subscribe.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
      }

      WS(src, t.publish)
  }

  def pipebuilder(conn: SocketConnection): LeafBuilder[ByteBuffer] =
    new Http1ServerStage(URITranslation.translateRoot("/http4s")(route), Some(conn)) with WebSocketSupport

  new SocketServerChannelFactory(pipebuilder, 12, 8*1024)
    .bind(new InetSocketAddress(8080))
    .run()
}
