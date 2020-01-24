package org.http4s.client

import java.io.IOException

/** Indicates a failure to establish a client connection, preserving the request key
  * that we tried to connect to.
  */
class ConnectionFailure(requestKey: RequestKey, cause: Throwable) extends IOException(cause) {
  override def getMessage(): String =
    s"Error connecting to $requestKey"
}
