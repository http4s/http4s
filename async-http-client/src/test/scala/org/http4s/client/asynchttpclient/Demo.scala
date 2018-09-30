import cats.effect._
import cats.implicits._
import fs2._
import org.http4s._
import org.http4s.client.asynchttpclient._
import org.http4s.websocket.WebsocketBits._
import scala.concurrent.duration._

object Foo extends IOApp {
  def run(args: List[String]) = {
    AsyncHttpClient.webSocketResource[IO]().use { wsClient =>
      val req = Request[IO](uri = Uri.uri("wss://echo.websocket.org/"))
      for {
        socket <- wsClient.connect(req)
        _ <- Stream("Hello", "from", "http4s").map(Text(_)).through(socket.send).compile.drain
        echo <- socket.receive.take(3).compile.toList.timeout(5.seconds)
        _ <- IO(println(echo))
      } yield echo
    }.as(ExitCode.Success)
  }
}
