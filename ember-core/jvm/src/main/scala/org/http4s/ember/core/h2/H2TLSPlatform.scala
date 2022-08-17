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

import javax.net.ssl.SSLEngine

private[h2] abstract class H2TLSPlatform {

  def transform(params: TLSParameters): TLSParameters =
    TLSParameters(
      algorithmConstraints = params.algorithmConstraints,
      applicationProtocols = List("http/1.1", "h2").some,
      cipherSuites = params.cipherSuites,
      enableRetransmissions = params.enableRetransmissions,
      endpointIdentificationAlgorithm = params.endpointIdentificationAlgorithm,
      maximumPacketSize = params.maximumPacketSize,
      protocols = params.protocols,
      serverNames = params.serverNames,
      sniMatchers = params.sniMatchers,
      useCipherSuitesOrder = params.useCipherSuitesOrder,
      needClientAuth = params.needClientAuth,
      wantClientAuth = params.wantClientAuth,
      handshakeApplicationProtocolSelector = { (_: SSLEngine, l: List[String]) =>
        l.find(_ === "h2").getOrElse("http/1.1")
      }.some,
    )
}
