package org.http4s
package client
package blaze

/** Create HTTP1 clients which will disconnect on completion of one request */
object SimpleHttp1Client {
  /** create a new simple client
    *
    * @param config blaze configuration object
    */
  def apply(config: BlazeClientConfig = BlazeClientConfig.defaultConfig): Client = {
    val (executionContext, shutdown) = bits.getExecutionContext(config)
    val manager: ConnectionManager[BlazeConnection] =
      ConnectionManager.basic(Http1Support(config, executionContext))

    BlazeClient(manager, config, manager.shutdown().flatMap(_ => shutdown))
  }
}
