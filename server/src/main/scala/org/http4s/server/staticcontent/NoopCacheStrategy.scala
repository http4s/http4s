package org.http4s.server.staticcontent

import org.http4s._
import scalaz.concurrent.Task


/** Cache strategy that doesn't cache anything, ever. */
object NoopCacheStrategy extends CacheStrategy {
  override def cache(uriPath: String, resp: Response): Task[Response] = Task.now(resp)
}
