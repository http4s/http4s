package fix
import java.util.concurrent.Executors

import cats.effect.IO
import javax.net.ssl.SSLContext
import org.http4s.client.blaze.{BlazeClientConfig, BlazeClientBuilder}
import org.http4s.{HttpRoutes, MediaType, Request, Response}
import org.http4s.dsl.io._
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, `User-Agent`}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.http4s.{ResponseCookie => Cookie}

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

  val fullConfig = BlazeClientConfig(
    responseHeaderTimeout = 1.second,
    idleTimeout = 2.second,
    requestTimeout = 3.second,
    userAgent = Some(`User-Agent`(AgentProduct("hello"))),
    maxTotalConnections = 1,
    maxWaitQueueLimit = 2,
    maxConnectionsPerRequestKey = _ => 1,
    sslContext = Some(SSLContext.getDefault),
    checkEndpointIdentification = false,
    maxResponseLineSize = 1,
    maxHeaderLength = 2,
    maxChunkSize = 3,
    lenientParser = false,
    bufferSize = 1,
    executionContext = global,
    group = None
  )

  val client = BlazeClientBuilder[IO](ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)))
  val client2 = BlazeClientBuilder[IO](global, Some(SSLContext.getDefault)).withRequestTimeout(3.second)
.withMaxChunkSize(3)
.withParserMode(org.http4s.client.blaze.ParserMode.Strict)
.withMaxTotalConnections(1)
.withIdleTimeout(2.second)
.withMaxWaitQueueLimit(2)
.withUserAgent(`User-Agent`(AgentProduct("hello")))
.withCheckEndpointAuthentication(false)
.withBufferSize(1)
.withResponseHeaderTimeout(1.second)
.withMaxResponseLineSize(1)
.withMaxHeaderLength(2)
.withMaxConnectionsPerRequestKey(_ => 1)
}
