package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

/** Create HTTP1 clients which will disconnect on completion of one request */
object SimpleHttp1Client {
  def apply(timeout: Duration = DefaultTimeout,
         bufferSize: Int = DefaultBufferSize,
           executor: ExecutorService = ClientDefaultEC,
         sslContext: Option[SSLContext] = None,
              group: Option[AsynchronousChannelGroup] = None) = {

    val ec = ExecutionContext.fromExecutor(executor)
    val cb = new Http1Support(bufferSize, timeout, ec, sslContext, group)
    new BlazeClient(cb, ec)
  }
}