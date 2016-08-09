package org.http4s
package server
package staticcontent

import fs2._

/** Cache strategy that doesn't cache anything, ever. */
object NoopCacheStrategy extends CacheStrategy {
  override def cache(uriPath: String, resp: Response): Task[Response] = Task.now(resp)
}
