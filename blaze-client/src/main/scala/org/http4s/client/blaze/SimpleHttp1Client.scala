package org.http4s
package client
package blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.headers.`User-Agent`
import scala.concurrent.duration.Duration

/** Create HTTP1 clients which will disconnect on completion of one request */
object SimpleHttp1Client {
  /** create a new simple client
    *
    * @param config blaze configuration object
    */
  def apply(config: BlazeClientConfig = BlazeClientConfig.defaultConfig) = {

    val (ex, shutdown) = bits.getExecutor(config)

    val manager = ConnectionManager.basic(Http1Support(config, ex))
    BlazeClient(manager, config, shutdown)
  }
}