package com.example.http4s.blaze

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import org.http4s.blaze.channel.SocketConnection
import org.http4s.blaze.channel.nio1.NIO1SocketServerGroup
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.server.HttpService
import org.http4s.server.blaze.{Http1ServerStage, WebSocketSupport}
import org.http4s.server.middleware.URITranslation
import org.http4s.websocket.WebsocketBits._

import scalaz.concurrent.Strategy
import scalaz.stream.{DefaultScheduler, Exchange}
import scalaz.stream.time.awakeEvery

object BlazeWebSocketExample extends App {

  import org.http4s.dsl._
  import org.http4s.server.websocket._

import scala.concurrent.duration._
  import scalaz.concurrent.Task
  import scalaz.stream.async.unboundedQueue
  import scalaz.stream.{Process, Sink}

/// code_ref: blaze_websocket_example

  val route = HttpService {
    case GET -> Root / "hello" =>
      Ok("Hello world.")

    case req@ GET -> Root / "ws" =>
      val src = awakeEvery(1.seconds)(Strategy.DefaultStrategy, DefaultScheduler).map{ d => Text(s"Ping! $d") }
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.delay( println(t))
        case f       => Task.delay(println(s"Unknown type: $f"))
      }
      WS(Exchange(src, sink))

    case req@ GET -> Root / "wsecho" =>
      val q = unboundedQueue[WebSocketFrame]
      val src = q.dequeue.collect {
        case Text(msg, _) => Text("You sent the server: " + msg)
      }

      WS(Exchange(src, q.enqueue))
  }

/// end_code_ref

  def pipebuilder(conn: SocketConnection): LeafBuilder[ByteBuffer] =
    new Http1ServerStage(URITranslation.translateRoot("/http4s")(route), Some(conn)) with WebSocketSupport

  NIO1SocketServerGroup.fixedGroup(12, 8*1024)
    .bind(new InetSocketAddress(8080), pipebuilder)
    .get  // yolo! Its just an example.
    .join()
}
