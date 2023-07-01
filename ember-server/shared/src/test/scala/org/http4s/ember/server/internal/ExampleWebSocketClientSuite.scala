package org.http4s.ember.server.internal

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Socket
import java.util.Base64
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.WebSocketHelpers.frameToBytes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.{Connection, `Sec-WebSocket-Key`, `Sec-WebSocket-Version`, Upgrade}
import org.http4s.server.Server
import org.http4s.server.websocket._
import org.http4s.testing.DispatcherIOFixture
import org.http4s.websocket._
import org.typelevel.ci._
import org.typelevel.vault._
import scodec.bits.ByteVector

class ExampleWebSocketClientSuite extends Http4sSuite with DispatcherIOFixture {

  def service[F[_]](wsBuilder: WebSocketBuilder2[F])(implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello")
        case GET -> Root / "ws-echo" =>
          println("ws-echhooooo")
          val sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame] = _.flatMap {
            case WebSocketFrame.Text(text, _) =>
              println(text)
              Stream(WebSocketFrame.Text(text))
            case _ =>
              println("unknowwwwn")
              Stream(WebSocketFrame.Text("unknown"))
          }
          wsBuilder.build(sendReceive)
        // case GET -> Root / "ws-close" =>
        //   val send = Stream(WebSocketFrame.Text("foo"))
        //   wsBuilder.build(send, _.void)
      }
      .orNotFound
  }

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpWebSocketApp(service[IO])
      .build

  val socketKey: Key[Socket[IO]] = Key.newKey[SyncIO, Socket[IO]].unsafeRunSync()

  val emberClient = EmberClientBuilder
    .default[IO]
    .buildWebSocket(socketKey)

  val supportedWebSocketVersion = 13L

  val upgradeCi = ci"upgrade"
  val webSocketProtocol = Protocol(ci"websocket", None)
  val connectionUpgrade = Connection(NonEmptyList.of(upgradeCi))
  val upgradeWebSocket = Upgrade(webSocketProtocol)
  val secWebSocketKey = "dGhlIHNhbXBsZSBub25jZQ=="

  def wsRequest(url: String): Request[IO] = Request[IO](
    method = Method.GET,
    uri = Uri.unsafeFromString(url),
    headers = Headers(
      upgradeWebSocket,
      connectionUpgrade,
      `Sec-WebSocket-Version`(supportedWebSocketVersion),
      new `Sec-WebSocket-Key`(ByteVector(Base64.getDecoder().decode(secWebSocketKey))),
    ),
  )
  // private[this] val clientTranscoder = new FrameTranscoder(false)

  // def frameToBytes(frame: WebSocketFrame): List[Chunk[Byte]] =
  //   clientTranscoder.frameToBuffer(frame).toList.map { buffer =>
  //     val bytes = new Array[Byte](buffer.remaining())
  //     buffer.get(bytes)
  //     Chunk.array(bytes)
  //   }

  private def fixture = (ResourceFunFixture(serverResource), dispatcher).mapN(FunFixture.map2(_, _))

  fixture.test("Ember WebSocket Client") { case (server, _) =>
    for {
      _ <- emberClient
        .use { client =>
          client
            .run(wsRequest(s"ws://${server.address.getHostName}:${server.address.getPort}/ws-echo"))
            .use { res =>
              res.attributes.lookup(socketKey) match {
                case Some(socket: Socket[IO]) =>
                  for {
                    _ <- frameToBytes(WebSocketFrame.Text("hello"), true).traverse_(c => socket.write(c))
                    _ <- socket.reads.take(7).evalTap(b => IO.println(b.toChar)).compile.drain
                    // received <- socket.reads.take(7).through(decodeFrames(true)).compile.drain
                  } yield ()
                case _ => IO.println("no socket")
              }
            }
        }
    } yield ()
  }
  
}
