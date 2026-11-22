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
import fs2.io.net.tls.SecureContext
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.SecureSession
import org.typelevel.scalaccompat.annotation.unused
import scodec.bits.ByteVector

trait TLSConnectionAttributesPlatform {

  /** Trusts [[TestCertificates.caPem]], and presents `identity` (certificate, key) if given. */
  protected def tlsContext(identity: Option[(String, String)]): Resource[IO, TLSContext[IO]] =
    Resource.pure(
      Network[IO].tlsContext.fromSecureContext(
        SecureContext(
          ca = List(TestCertificates.caPem.asRight).some,
          cert = identity.map { case (cert, _) => List(cert.asRight) },
          key = identity.map { case (_, key) => List(SecureContext.Key(key.asRight, None)) },
        )
      )
    )

  // Node rejects an IP address as the SNI server name, which Ember sends for 127.0.0.1.
  // Only needed on JS: Native must keep SNI, as s2n checks the server certificate against it.
  protected def configureClient(builder: EmberClientBuilder[IO]): EmberClientBuilder[IO] =
    builder.withoutServerNameIndication

  protected val requireClientCert: TLSParameters =
    TLSParameters(requestCert = true.some, rejectUnauthorized = true.some)

  /** Each way to require a client certificate. */
  protected val requireClientCertVariants: List[TLSParameters] =
    List(requireClientCert, TLSParameters(requestCert = true.some))

  protected val optionalClientCert: TLSParameters =
    TLSParameters(requestCert = true.some, rejectUnauthorized = false.some)

  /** Node completes the handshake with an untrusted client certificate when it is optional. */
  protected val acceptsUntrustedOptionalClientCert: Boolean = true

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
