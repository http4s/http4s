/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package staticcontent

import cats.effect.Concurrent

/** Cache strategy that doesn't cache anything, ever. */
class NoopCacheStrategy[F[_]] extends CacheStrategy[F] {
  override def cache(uriPath: Uri.Path, resp: Response[F])(implicit F: Concurrent[F]): F[Response[F]] =
    F.pure(resp)
}

object NoopCacheStrategy {
  def apply[F[_]]: NoopCacheStrategy[F] = new NoopCacheStrategy[F]
}
