/*
 * Copyright 2021 http4s.org
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
package fetchclient

import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._
import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import java.util.Arrays
import java.util.Locale
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.http4s.client.testroutes.GetRoutes
import org.http4s.client.ClientRouteTestBattery
import cats.effect.Resource
import org.http4s.client.Client

class FetchClientSuite extends ClientRouteTestBattery("FetchClient") with Http4sSuite {
  override def clientResource: Resource[IO, Client[IO]] = Resource.pure(FetchClient[IO])
}
