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

package org.http4s.client

package object blaze {
  @deprecated("use org.http4s.blaze.client.BlazeClientBuilder", "0.22")
  type BlazeClientBuilder[F[_]] = org.http4s.blaze.client.BlazeClientBuilder[F]

  @deprecated("use org.http4s.blaze.client.BlazeClientBuilder", "0.22")
  val BlazeClientBuilder = org.http4s.blaze.client.BlazeClientBuilder
}
