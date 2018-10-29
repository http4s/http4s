package fix
import java.util.concurrent.Executors

import cats.effect.IO
import org.http4s.client.blaze.{BlazeClientConfig, BlazeClientBuilder}
import org.http4s.{HttpRoutes, MediaType, Request, Response}
import org.http4s.dsl.io._
import org.http4s.client.Client

import scala.concurrent.ExecutionContext
import org.http4s.{ResponseCookie => Cookie}
import scala.concurrent.ExecutionContext.Implicits.global

object Http4s018To020 {
  // Add code that needs fixing here.

  def service(): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root => Ok().withEntity()
  }

  def serviceWithoutExplicitType(): HttpRoutes[IO] = HttpRoutes.of {
    case req @ GET -> Root => Ok()
  }

  val requestWithBody: Request[IO] = Request().withEntity("hello")
  def responseWithBody: IO[Response[IO]] = Ok().withEntity("world")

  val x = MediaType.application.`atom+xml`
  MediaType.application.`vnd.google-earth.kml+xml`

  val config = BlazeClientConfig.defaultConfig.copy(executionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)))

  val client = BlazeClientBuilder[IO](ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)))
  val client2 = BlazeClientBuilder[IO](global)
}
