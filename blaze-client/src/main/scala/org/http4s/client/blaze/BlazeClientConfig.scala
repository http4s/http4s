package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.headers.`User-Agent`

import scala.concurrent.duration.Duration

/** Config object for the blaze clients
  *
  * @param idleTimeout duration that a connection can wait without traffic before timeout
  * @param requestTimeout maximum duration for a request to complete before a timeout
  * @param bufferSize internal buffer size of the blaze client
  * @param userAgent optional custom user agent header
  * @param executor thread pool where asynchronous computations will be performed
  * @param sslContext optional custom `SSLContext` to use to replace the default
  * @param endpointAuthentication require endpoint authentication for encrypted connections
  * @param group custom `AsynchronousChannelGroup` to use other than the system default
  */
case class BlazeClientConfig(
                              idleTimeout: Duration,
                              requestTimeout: Duration,
                              bufferSize: Int,
                              userAgent: Option[`User-Agent`],
                              executor: ExecutorService,
                              sslContext: Option[SSLContext],
                              endpointAuthentication: Boolean,
                              group: Option[AsynchronousChannelGroup]
                            )

object BlazeClientConfig {
  /** Default user configuration */
  val defaultConfig = BlazeClientConfig(
    idleTimeout = bits.DefaultTimeout,
    requestTimeout = Duration.Inf,
    bufferSize = bits.DefaultBufferSize,
    userAgent = bits.DefaultUserAgent,
    executor = bits.ClientDefaultEC,
    sslContext = None,
    endpointAuthentication = true,
    group = None
  )
}
