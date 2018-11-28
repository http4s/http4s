/*
rule = v0_20
*/
package fix
import java.util.concurrent.Executors

import cats.effect.IO
import javax.net.ssl.SSLContext
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.client.Client
import org.http4s.headers.{AgentProduct, `User-Agent`}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.http4s.Cookie
import org.http4s.MediaType.`image/jpeg`
import org.http4s.MediaType._
import org.http4s.server.blaze.BlazeBuilder

object Http4s018To020 {
  // Add code that needs fixing here.

  def service(): HttpService[IO] = HttpService[IO] {
    case _ @ GET -> Root => Ok().withBody("hello")
  }

  def serviceWithoutExplicitType(): HttpService[IO] = HttpService {
    case _ @ GET -> Root => Ok()
  }

  val requestWithBody: IO[Request[IO]] = Request().withBody("hello")
  val requestWithBody2 = {
    val nested = {
      implicit def encoder[A]: EntityEncoder[IO, A] = ???
      Some("world").map(Request[IO]().withBody)
    }
    nested
  }
  def responseWithBody: IO[Response[IO]] = Ok().withBody("world")
  def responseWithBody2: IO[Response[IO]] = Ok().flatMap(_.withBody("world"))

  val x = MediaType.`application/atom+xml`
  val y = MediaType.`application/vnd.google-earth.kml+xml`
  val z = `image/jpeg`
  val zz = org.http4s.MediaType.`application/atom+xml`

  val cookie: Option[Cookie] = None

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

  val client: IO[Client[IO]] = Http1Client[IO](config)
  val clientStream = Http1Client.stream[IO](fullConfig)
  val client2 = Http1Client[IO](fullConfig)
  val client3 = Http1Client.stream[IO](BlazeClientConfig(
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
  ))

  val server = BlazeBuilder[IO]
    .bindHttp(8080)
    .mountService(service, "/http4s")
    .serve
}
