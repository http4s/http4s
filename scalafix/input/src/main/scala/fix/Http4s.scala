/*
rule = Http4s
*/
package fix
import cats.effect.IO
import org.http4s.{Cookie, HttpService, MediaType, Request, Response}
import org.http4s.dsl.io._

object Http4s {
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
}
