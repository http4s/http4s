/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client

import org.http4s.Uri.{Authority, Scheme}

/** Represents a key for requests that can conceivably share a [[Connection]]. */
final case class RequestKey(scheme: Scheme, authority: Authority) {
  override def toString = s"${scheme.value}://${authority}"
}

object RequestKey {
  def fromRequest[F[_]](request: Request[F]): RequestKey = {
    val uri = request.uri
    RequestKey(uri.scheme.getOrElse(Scheme.http), uri.authority.getOrElse(Authority()))
  }
}
