/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.IO

class CompareEmberHttp1ServerSuite
    extends WireSuite(EmberTarget[IO], Protocol.Http1, Role.Server, comparison = true)
class CompareEmberHttp1ClientSuite
    extends WireSuite(EmberTarget[IO], Protocol.Http1, Role.Client, comparison = true)
class CompareEmberHttp2ServerSuite
    extends WireSuite(EmberTarget[IO], Protocol.Http2, Role.Server, comparison = true)
class CompareEmberHttp2ClientSuite
    extends WireSuite(EmberTarget[IO], Protocol.Http2, Role.Client, comparison = true)
