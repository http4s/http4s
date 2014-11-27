package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup

import scala.concurrent.duration._

class PooledHttp1Client(maxPooledConnections: Int = 10,
                                     timeout: Duration = defaultTimeout,
                                  bufferSize: Int = defaultBufferSize,
                                       group: Option[AsynchronousChannelGroup] = None)
  extends PooledClient(maxPooledConnections, timeout, bufferSize, group) with Http1SSLSupport
