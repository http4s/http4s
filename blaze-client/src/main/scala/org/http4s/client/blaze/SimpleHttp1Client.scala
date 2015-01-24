package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import scala.concurrent.duration.Duration

/** Create HTTP1 clients which will disconnect on completion of one request */
object SimpleHttp1Client {
  def apply(timeout: Duration = DefaultTimeout,
         bufferSize: Int = DefaultBufferSize,
           executor: ExecutorService = ClientDefaultEC,
         sslContext: Option[SSLContext] = None,
              group: Option[AsynchronousChannelGroup] = None) =
    new BlazeClient(new Http1Support(bufferSize, timeout, executor, sslContext, group))
}