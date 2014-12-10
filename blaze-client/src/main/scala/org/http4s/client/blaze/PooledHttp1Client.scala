package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService

import scala.concurrent.duration._

class PooledHttp1Client protected (maxPooledConnections: Int,
                                  protected val timeout: Duration,
                                             bufferSize: Int,
                                               executor: ExecutorService,
                                                  group: Option[AsynchronousChannelGroup])
  extends PooledClient(maxPooledConnections, bufferSize, executor, group) with Http1SSLSupport

/** Http client which will attempt to recycle connections */
object PooledHttp1Client {

  /** Construct a new PooledHttp1Client */
  def apply(maxPooledConnections: Int = 10,
                         timeout: Duration = DefaultTimeout,
                      bufferSize: Int = DefaultBufferSize,
                        executor: ExecutorService = ClientDefaultEC,
                           group: Option[AsynchronousChannelGroup] = None) =
    new PooledHttp1Client(maxPooledConnections, timeout, bufferSize, executor, group)
}
