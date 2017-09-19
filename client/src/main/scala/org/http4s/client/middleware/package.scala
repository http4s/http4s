package org.http4s
package client

import scala.concurrent.duration.FiniteDuration

package object middleware {

  /** A retry policy is a function of the request, the result (either a
    * throwable or a response), and the number of unsuccessful attempts
    * and returns either None (no retry) or Some duration, after which
    * the request will be retried.
    */
  type RetryPolicy[F[_]] =
    (Request[F], Either[Throwable, Response[F]], Int) => Option[FiniteDuration]
}
