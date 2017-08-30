package org.http4s
package client

import scala.concurrent.duration.FiniteDuration
import scalaz.\/

package object middleware {
  /** A retry policy is a function of the request, the result (either a
    * throwable or a response), and the number of unsuccessful attempts
    * and returns either None (no retry) or Some duration, after which
    * the request will be retried.
    */
  type RetryPolicy = (Request, Throwable \/ Response, Int) => Option[FiniteDuration]
}
