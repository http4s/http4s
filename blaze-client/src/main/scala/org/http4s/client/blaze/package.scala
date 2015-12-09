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

  /** Lenient blaze client
    *
    * This is a variant of default client which silently drops headers with illegal characters. */
  val lenientClient = SimpleHttp1Client(
    lenient = true,
    idleTimeout = bits.DefaultTimeout,
    bufferSize = bits.DefaultBufferSize,
    executor = bits.ClientDefaultEC,
    sslContext = None
  )

}
