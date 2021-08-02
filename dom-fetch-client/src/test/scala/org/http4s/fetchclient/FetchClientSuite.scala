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
package dom

import cats.effect.IO
import cats.effect.Resource
import org.http4s.client.Client
import org.http4s.client.ClientRouteTestBattery

class FetchClientSuite extends ClientRouteTestBattery("FetchClient") with Http4sSuite {
  override def clientResource: Resource[IO, Client[IO]] = Resource.pure(FetchClient[IO])
}
