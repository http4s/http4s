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

package org.http4s.ember.server

import cats.effect._
import cats.syntax.all._
import fs2.io.net.Network
import fs2.io.net.tls.CertAuthType
import fs2.io.net.tls.CertChainAndKey
import fs2.io.net.tls.S2nConfig
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.SecureSession
import org.typelevel.scalaccompat.annotation.unused
import scodec.bits.ByteVector

trait TLSConnectionAttributesPlatform {

  /** Trusts [[TestCertificates.caPem]], and presents `identity` (certificate, key) if given. */
  protected def tlsContext(identity: Option[(String, String)]): Resource[IO, TLSContext[IO]] = {
    val trusting = S2nConfig.builder.withPemsToTrustStore(List(TestCertificates.caPem))
    val builder = identity.fold(trusting) { case (cert, key) =>
      trusting.withCertChainAndKeysToStore(List(CertChainAndKey(cert, key)))
    }
    builder.build[IO].map(Network[IO].tlsContext.fromS2nConfig(_))
  }

  protected def configureClient(builder: EmberClientBuilder[IO]): EmberClientBuilder[IO] =
    builder

  protected val requireClientCert: TLSParameters =
    TLSParameters(clientAuthType = CertAuthType.Required.some)

  /** Each way to require a client certificate. */
  protected val requireClientCertVariants: List[TLSParameters] = List(requireClientCert)

  protected val optionalClientCert: TLSParameters =
    TLSParameters(clientAuthType = CertAuthType.Optional.some)

  /** s2n fails the handshake if an optional client certificate does not verify. */
  protected val acceptsUntrustedOptionalClientCert: Boolean = false

  /** fs2's Native TLS can loop forever, uncancelably, when a socket is closed after a write was
    * cancelled partway or failed. Ember's HTTP/2 does both: it cancels its write loop on
    * teardown, and the client writes a GOAWAY after the server drops the connection.
    */
  protected val skipHttp2OverTLS: Boolean = true

  /** The client certificate chain the server should report for [[TestCertificates.clientPem]]:
    * the client's certificate followed by the trusted root that verified it.
    */
  protected val clientChain: List[ByteVector] =
    List(
      TestCertificates.der(TestCertificates.clientPem),
      TestCertificates.der(TestCertificates.caPem),
    )

  protected def certificates(session: SecureSession): List[ByteVector] = session.peerCertificates

  // Only the JVM SecureSession has details beyond the certificates to check
  protected def assertSessionDetails(@unused session: SecureSession): Unit = ()
}
