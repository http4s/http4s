package fix
import java.util.concurrent.Executors

import cats.effect.IO
import javax.net.ssl.SSLContext
import org.http4s.client.blaze.{BlazeClientConfig, BlazeClientBuilder}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, `User-Agent`}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.http4s.MediaType._
import org.http4s.ResponseCookie
import org.http4s.MediaType.{ application, image }
import org.http4s.server.blaze.BlazeServerBuilder

object Http4s018To020 {
  // Add code that needs fixing here.

  def service(): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _ @ GET -> Root => Ok().map(_.withEntity("hello"))
  }

  def serviceWithoutExplicitType(): HttpRoutes[IO] = HttpRoutes.of {
    case _ @ GET -> Root => Ok()
  }

  val requestWithBody: Request[IO] = Request().withEntity("hello")
  val requestWithBody2 = {
    val nested = {
      implicit def encoder[A]: EntityEncoder[IO, A] = ???
      Some("world").map(Request[IO]().withEntity)
    }
    nested
  }
  def responseWithBody: IO[Response[IO]] = Ok().map(_.withEntity("world"))
  def responseWithBody2: IO[Response[IO]] = Ok().map(_.withEntity("world"))

  val x = MediaType.application.`atom+xml`
  val y = MediaType.application.`vnd.google-earth.kml+xml`
  val z = image.jpeg
  val zz = org.http4s.MediaType.application.`atom+xml`

  val cookie: Option[ResponseCookie] = None

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

  val client: IO[(Client[IO], IO[Unit])] = BlazeClientBuilder[IO](ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))).allocated
  val clientStream = BlazeClientBuilder[IO](global, Some(SSLContext.getDefault)).withRequestTimeout(3.second).withMaxChunkSize(3).withParserMode(org.http4s.client.blaze.ParserMode.Strict).withMaxTotalConnections(1).withIdleTimeout(2.second).withMaxWaitQueueLimit(2).withUserAgent(`User-Agent`(AgentProduct("hello"))).withCheckEndpointAuthentication(false).withBufferSize(1).withResponseHeaderTimeout(1.second).withMaxResponseLineSize(1).withMaxHeaderLength(2).withMaxConnectionsPerRequestKey(_ => 1).stream
  val client2 = BlazeClientBuilder[IO](global, Some(SSLContext.getDefault)).withRequestTimeout(3.second).withMaxChunkSize(3).withParserMode(org.http4s.client.blaze.ParserMode.Strict).withMaxTotalConnections(1).withIdleTimeout(2.second).withMaxWaitQueueLimit(2).withUserAgent(`User-Agent`(AgentProduct("hello"))).withCheckEndpointAuthentication(false).withBufferSize(1).withResponseHeaderTimeout(1.second).withMaxResponseLineSize(1).withMaxHeaderLength(2).withMaxConnectionsPerRequestKey(_ => 1).allocated
  val client3 = BlazeClientBuilder[IO](global, Some(SSLContext.getDefault)).withRequestTimeout(3.second).withMaxChunkSize(3).withParserMode(org.http4s.client.blaze.ParserMode.Strict).withMaxTotalConnections(1).withIdleTimeout(2.second).withMaxWaitQueueLimit(2).withUserAgent(`User-Agent`(AgentProduct("hello"))).withCheckEndpointAuthentication(false).withBufferSize(1).withResponseHeaderTimeout(1.second).withMaxResponseLineSize(1).withMaxHeaderLength(2).withMaxConnectionsPerRequestKey(_ => 1).stream

  val server = BlazeServerBuilder[IO]
    .bindHttp(8080)
    .mountService(service, "/http4s")
    .serve
}
