/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.core.h2

import cats.syntax.all._
import fs2.io.net.tls.TLSParameters

private[h2] abstract class H2TLSPlatform {

  def transform(params: TLSParameters): TLSParameters =
    TLSParameters(
      requestCert = params.requestCert,
      rejectUnauthorized = params.rejectUnauthorized,
      alpnProtocols = List("h2", "http/1.1").some,
      sniCallback = params.sniCallback,
      session = params.session,
      requestOCSP = params.requestOCSP,
      pskCallback = params.pskCallback,
      servername = params.servername,
      checkServerIdentity = params.checkServerIdentity,
      minDHSize = params.minDHSize,
    )

}
