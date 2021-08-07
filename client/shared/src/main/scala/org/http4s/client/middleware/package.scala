/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
