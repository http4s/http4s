package org.http4s.server.staticcontent

import org.http4s._

import scalaz.concurrent.Task


/** Cache the body of a [[Response]] for future use
  *
  * A [[CacheStrategy]] acts like a after filter in that it can look at the
  * [[Response]] and [[Uri]] of the [[Request]] and decide if the body for
  * the response has already been cached, needs caching, or to let it pass through.
  */
trait CacheStrategy {

  /** Performs the caching operations */
  def cache(path: Uri, resp: Response): Task[Response]
}
