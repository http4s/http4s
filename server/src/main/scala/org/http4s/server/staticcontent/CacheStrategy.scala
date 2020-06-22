/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package staticcontent

import cats.effect.Sync

/** Cache the body of a [[Response]] for future use
  *
  * A [[CacheStrategy]] acts like a after filter in that it can look at the
  * [[Response]] and [[Uri]] of the [[Request]] and decide if the body for
  * the response has already been cached, needs caching, or to let it pass through.
  */
trait CacheStrategy[F[_]] {

  /** Performs the caching operations */
  def cache(uriPath: Uri.Path, resp: Response[F])(implicit F: Sync[F]): F[Response[F]]
}
