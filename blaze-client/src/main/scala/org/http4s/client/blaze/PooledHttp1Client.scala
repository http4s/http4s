package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class PooledHttp1Client(maxPooledConnections: Int = 10,
                       protected val timeout: Duration = defaultTimeout,
                                  bufferSize: Int = defaultBufferSize,
                                    executor: ExecutionContext = defaultEC,
                                       group: Option[AsynchronousChannelGroup] = None)
  extends PooledClient(maxPooledConnections, bufferSize, executor, group) with Http1SSLSupport
