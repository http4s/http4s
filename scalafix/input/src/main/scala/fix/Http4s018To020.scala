/*
rule = Http4s018To020
*/
package fix
import java.util.concurrent.Executors

import cats.effect.IO
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}
import org.http4s.{Cookie, HttpService, MediaType, Request, Response}
import org.http4s.dsl.io._
import org.http4s.client.Client

import scala.concurrent.ExecutionContext

object Http4s018To020 {
  // Add code that needs fixing here.

  def service(): HttpService[IO] = HttpService[IO] {
    case req @ GET -> Root => Ok().withBody()
  }

  def serviceWithoutExplicitType(): HttpService[IO] = HttpService {
    case req @ GET -> Root => Ok()
  }

  val requestWithBody: IO[Request[IO]] = Request().withBody("hello")
  def responseWithBody: IO[Response[IO]] = Ok().withBody("world")

  val x = MediaType.`application/atom+xml`
  MediaType.`application/vnd.google-earth.kml+xml`

  val config = BlazeClientConfig.defaultConfig.copy(executionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)))

  val client = Http1Client[IO](config)
  val client2 = Http1Client[IO]()
}
