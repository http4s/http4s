package org.http4s
package server
package staticcontent

import fs2._

/** Cache the body of a [[Response]] for future use
  *
  * A [[CacheStrategy]] acts like a after filter in that it can look at the
  * [[Response]] and [[Uri]] of the [[Request]] and decide if the body for
  * the response has already been cached, needs caching, or to let it pass through.
  */
trait CacheStrategy {
  /** Performs the caching operations */
  def cache(uriPath: String, resp: Response): Task[Response]
}
