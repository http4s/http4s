/*
 * Copyright 2014-2021 http4s.org
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

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationLong

package object client extends ClientTypes {
  object defaults {
    val ConnectTimeout = 10.seconds
    val RequestTimeout = 45.seconds
  }
}

trait ClientTypes {
  import org.http4s.client._

  type ConnectionBuilder[F[_], A <: Connection[F]] = RequestKey => F[A]

  type Middleware[F[_]] = Client[F] => Client[F]
}

case object WaitQueueTimeoutException
    extends TimeoutException("In wait queue for too long, timing out request.")
