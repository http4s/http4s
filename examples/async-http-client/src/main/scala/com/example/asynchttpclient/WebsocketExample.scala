package com.example.asynchttpclient

import java.nio.ByteBuffer
import org.asynchttpclient.ws.{ DefaultWebSocketListener, WebSocketUpgradeHandler }
import org.asynchttpclient.ws.WebSocketListener
import org.http4s._
import org.http4s.client.asynchttpclient._
import org.http4s.websocket.WebsocketBits._
import scala.concurrent.duration._
import scalaz.concurrent.{ Strategy, Task }
import scalaz.stream.Cause.CausedBy
import scalaz.stream.Process.Halt
import scalaz.stream.{ Exchange, time }
import scalaz.stream.Process
import scalaz.stream.io.stdOutLines
import scalaz.stream.wye.{Request => WyeRequest}

object WebsocketExample extends App {
  val client = AsyncHttpClient()

  val send = Process(Text("Hello"), Text("from"), Text("WebsocketExample")).toSource
  client.webSocket(Request(Method.GET, Uri.uri("ws://echo.websocket.org/?encoding=text"))) { ws =>
    ws.exchange.run(send, WyeRequest.L).map {
      case Text(str, _) =>
        str
      case frame =>
        s"Unexpected frame: ${frame}"
    }.through(stdOutLines.contramap(msg => s"Echoed: $msg")).run
  }.run

  client.shutdown.run
}
