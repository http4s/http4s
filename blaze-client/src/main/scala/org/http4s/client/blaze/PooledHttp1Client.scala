package org.http4s.client.blaze

import java.nio.channels.AsynchronousChannelGroup

class PooledHttp1Client(maxPooledConnections: Int = 10,
                        bufferSize: Int = 8*1024,
                        group: Option[AsynchronousChannelGroup] = None)
  extends PooledClient(maxPooledConnections, bufferSize, group) with Http1SSLSupport
