package org.http4s
package client

import scalaz.concurrent.Task


package object blaze {

  /** Factory function for new client connections.
    *
    * The connections must be 'fresh' in the sense that they are newly created
    * and failure of the resulting client stage is a sign of connection trouble
    * not due to typical timeouts etc.
    */
  type ConnectionBuilder = Request => Task[BlazeClientStage]

  /** Default blaze client
    *
    * This client will create a new connection for every request. */
  val defaultClient = SimpleHttp1Client(
    idleTimeout = bits.DefaultTimeout,
    bufferSize = bits.DefaultBufferSize,
    executor = bits.ClientDefaultEC,
    sslContext = None
  )
}
