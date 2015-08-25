package org.http4s.server.jetty

import com.codahale.metrics.Timer
import com.codahale.metrics.jetty9.{InstrumentedConnectionFactory => MInstrumentedConnectionFactory}
import org.eclipse.jetty.server.ConnectionFactory

/**
 * Workaround for https://github.com/http4s/http4s/issues/396
 */
class InstrumentedConnectionFactory (connectionFactory: ConnectionFactory, timer: Timer)
  extends MInstrumentedConnectionFactory(connectionFactory, timer)
{
  def getProtocols: java.util.List[String] = connectionFactory.getProtocols
}
