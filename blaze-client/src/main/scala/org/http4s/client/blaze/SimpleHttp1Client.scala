package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.headers.`User-Agent`
import scala.concurrent.duration.Duration

/** Create HTTP1 clients which will disconnect on completion of one request */
object SimpleHttp1Client {
  def apply(idleTimeout: Duration = bits.DefaultTimeout,
     requestTimeout: Duration = Duration.Inf,
         bufferSize: Int = bits.DefaultBufferSize,
          userAgent: Option[`User-Agent`] = bits.DefaultUserAgent,
           executor: ExecutorService = bits.ClientDefaultEC,
         sslContext: Option[SSLContext] = None,
              group: Option[AsynchronousChannelGroup] = None) = {
    val manager = ConnectionManager.basic(Http1Support(bufferSize,  userAgent, executor, sslContext, group))
    new BlazeClient(manager, idleTimeout, requestTimeout)
  }
}