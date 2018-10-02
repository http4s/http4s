import cats.effect._
import cats.implicits._
import fs2._
import org.http4s._
import org.http4s.client.asynchttpclient._
import org.http4s.websocket.WebsocketBits._

object Foo extends IOApp {
  def run(args: List[String]) =
    AsyncHttpClient
      .webSocketResource[IO]()
      .use { wsClient =>
        val req = Request[IO](uri = Uri.uri("wss://echo.websocket.org/"))
        for {
          socket <- wsClient.connect(req)
          _ <- socket.write1(Text("Hello"))
          _ <- Stream("from", "http4s").map(Text(_)).through(socket.write).compile.drain
          _ <- socket.write1(Close())
          echo <- socket.read.take(4).compile.toList
          _ <- IO(println(echo))
        } yield echo
      }
      .as(ExitCode.Success)
}
