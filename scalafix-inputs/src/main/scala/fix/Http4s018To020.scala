/*
rule = Http4s018To020
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

object Http4s018To020 {
  // Add code that needs fixing here.

  def service(): HttpService[IO] = HttpService[IO] {
    case _ @ GET -> Root => Ok().withBody("hello")
  }

  def serviceWithoutExplicitType(): HttpService[IO] = HttpService {
    case _ @ GET -> Root => Ok()
  }

  val requestWithBody: IO[Request[IO]] = Request().withBody("hello")
  def responseWithBody: IO[Response[IO]] = Ok().withBody("world")

  val x = MediaType.`application/atom+xml`
  val y = MediaType.`application/vnd.google-earth.kml+xml`

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
}
