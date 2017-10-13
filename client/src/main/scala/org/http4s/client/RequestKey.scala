package org.http4s
package client

import org.http4s.Uri.{Authority, Scheme}

/** Represents a key for requests that can conceivably share a [[Connection]]. */
final case class RequestKey(scheme: Scheme, authority: Authority)

object RequestKey {
  def fromRequest[F[_]](request: Request[F]): RequestKey = {
    val uri = request.uri
    RequestKey(uri.scheme.getOrElse(Scheme.http), uri.authority.getOrElse(Authority()))
  }
}
