package org.http4s.client
package blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.client.impl.DefaultExecutor
import org.http4s.headers.`User-Agent`

import scala.concurrent.duration.Duration

/** Config object for the blaze clients
  *
  * @param idleTimeout duration that a connection can wait without traffic before timeout
  * @param requestTimeout maximum duration for a request to complete before a timeout
  * @param userAgent optional custom user agent header
  * @param sslContext optional custom `SSLContext` to use to replace the default
  * @param endpointAuthentication require endpoint authentication for encrypted connections
  * @param maxResponseLineSize maximum length of the request line
  * @param maxHeaderLength maximum length of headers
  * @param maxChunkSize maximum size of chunked content chunks
  * @param bufferSize internal buffer size of the blaze client
  * @param executor thread pool where asynchronous computations will be performed
  * @param group custom `AsynchronousChannelGroup` to use other than the system default
  */
case class BlazeClientConfig( //
                              idleTimeout: Duration,
                              requestTimeout: Duration,
                              userAgent: Option[`User-Agent`],

                              // security options
                              sslContext: Option[SSLContext],
                              endpointAuthentication: Boolean,

                              // parser options
                              maxResponseLineSize: Int,
                              maxHeaderLength: Int,
                              maxChunkSize: Int,

                              // pipeline management
                              bufferSize: Int,
                              executor: ExecutorService,
                              group: Option[AsynchronousChannelGroup]
                            )

object BlazeClientConfig {
  /** Default user configuration
    *
    * @param executor executor on which to run computations.
    *                 If the default `ExecutorService` is used it will be shutdown with the client
    */
  def defaultConfig(executor: ExecutorService = DefaultExecutor.newClientDefaultExecutorService("blaze-client")) =
    BlazeClientConfig(
      idleTimeout = bits.DefaultTimeout,
      requestTimeout = Duration.Inf,
      userAgent = bits.DefaultUserAgent,

      sslContext = None,
      endpointAuthentication = true,

      maxResponseLineSize = 4*1024,
      maxHeaderLength = 40*1024,
      maxChunkSize = Integer.MAX_VALUE,

      bufferSize = bits.DefaultBufferSize,
      executor = executor,
      group = None
    )
}
