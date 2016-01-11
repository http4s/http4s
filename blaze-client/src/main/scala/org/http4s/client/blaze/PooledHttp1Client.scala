package org.http4s
package client
package blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.headers.`User-Agent`

import scala.concurrent.duration.Duration

/** Create a HTTP1 client which will attempt to recycle connections */
object PooledHttp1Client {

  /** Construct a new PooledHttp1Client */
  def apply( maxTotalConnections: Int = 10,
                     idleTimeout: Duration = bits.DefaultTimeout,
                  requestTimeout: Duration = Duration.Inf,
                       userAgent: Option[`User-Agent`] = bits.DefaultUserAgent,
                      bufferSize: Int = bits.DefaultBufferSize,
                        executor: ExecutorService = bits.ClientDefaultEC,
                      sslContext: Option[SSLContext] = None,
          endpointAuthentication: Boolean = true,
                           group: Option[AsynchronousChannelGroup] = None) = {
    val http1 = Http1Support(bufferSize, userAgent, executor, sslContext, endpointAuthentication, group)
    val pool = ConnectionManager.pool(http1, maxTotalConnections)
    BlazeClient(pool, idleTimeout, requestTimeout)
  }
}
