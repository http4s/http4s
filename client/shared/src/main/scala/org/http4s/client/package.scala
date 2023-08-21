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

import org.typelevel.scalaccompat.annotation._

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration

package client {
  @nowarn213("msg=package object inheritance is deprecated")
  object `package` extends ClientTypes {
    object defaults {
      val ConnectTimeout: FiniteDuration = 10.seconds
      val RequestTimeout: FiniteDuration = 45.seconds
    }
  }
}

trait ClientTypes {
  import org.http4s.client._

  @deprecated("Is a Blaze detail.  Will be removed from public API.", "0.23.8")
  type ConnectionBuilder[F[_], A <: Connection[F]] = RequestKey => F[A]

  type Middleware[F[_]] = Client[F] => Client[F]
}

case object WaitQueueTimeoutException
    extends TimeoutException("In wait queue for too long, timing out request.")
