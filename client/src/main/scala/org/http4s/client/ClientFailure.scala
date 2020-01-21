package org.http4s.client

import java.io.IOException
import java.net.InetSocketAddress

/** Indicates a failure in the client when executing a request preserving the context (requestKey and upstream address)
  */
class ClientFailure(requestKey: RequestKey, upstream: InetSocketAddress, cause: Throwable)
    extends IOException(cause) {
  override def getMessage(): String =
    s"Error connecting to $requestKey using address $upstream (unresolved: ${upstream.isUnresolved})"
}
