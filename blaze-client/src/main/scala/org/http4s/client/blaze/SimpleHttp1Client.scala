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
    val manager = ConnectionManager.basic(Http1Support(config))
    BlazeClient(manager, config, manager.shutdown())
  }
}
