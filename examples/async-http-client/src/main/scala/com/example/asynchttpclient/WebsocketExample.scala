package com.example.asynchttpclient

import org.http4s._
import org.http4s.client.asynchttpclient._
import org.http4s.websocket.Websocket
import org.http4s.websocket.WebsocketBits._
import scalaz.stream.Exchange
import scalaz.stream.Process
import scalaz.stream.io.stdOutLines

object WebsocketExample extends App {
  val client = AsyncHttpClient()

  client.websocket(Request(Method.GET, Uri.uri("ws://demo.http4s.org/wschat/Test"))).flatMap { ws: Exchange[WebSocketFrame, WebSocketFrame] =>
    val send = Process("Hello", "from", "WebsocketExample").toSource.map(Text(_))
    val recv = ws.run(send).map {
      case Text(str, _) => str
      case frame => s"Unexpected frame: ${frame}"
    }
    recv.through(stdOutLines.contramap(msg => s"Echoed: $msg")).run
  }.run

  client.shutdown.run
}
