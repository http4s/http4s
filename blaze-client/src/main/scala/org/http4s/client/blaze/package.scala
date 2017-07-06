package org.http4s
package client

package object blaze {

  /** Default blaze client
    *
    * This client will create a new connection for every request.
    */
  lazy val defaultClient: Client = SimpleHttp1Client(BlazeClientConfig.defaultConfig)
}
