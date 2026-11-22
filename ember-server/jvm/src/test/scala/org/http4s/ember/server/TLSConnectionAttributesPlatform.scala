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
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import org.http4s.Http4sSuite
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.SecureSession
import scodec.bits.ByteVector

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Principal
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager

trait TLSConnectionAttributesPlatform { self: Http4sSuite =>

  private val password = "password".toCharArray

  private def certificate(pem: String): Certificate =
    CertificateFactory
      .getInstance("X.509")
      .generateCertificate(new ByteArrayInputStream(pem.getBytes(UTF_8)))

  private def emptyKeyStore: KeyStore = {
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null, null)
    ks
  }

  /** Presents `alias` as the client certificate even if the server does not list its issuer as
    * acceptable. The default key manager would otherwise send no certificate for the untrusted
    * one, and the untrusted-certificate tests would only repeat the no-certificate case.
    */
  private def alwaysPresent(km: X509ExtendedKeyManager, alias: String): X509ExtendedKeyManager =
    new X509ExtendedKeyManager {
      override def chooseClientAlias(
          keyType: Array[String],
          issuers: Array[Principal],
          socket: java.net.Socket,
      ): String = alias
      override def chooseEngineClientAlias(
          keyType: Array[String],
          issuers: Array[Principal],
          engine: SSLEngine,
      ): String = alias
      override def chooseServerAlias(
          keyType: String,
          issuers: Array[Principal],
          socket: java.net.Socket,
      ): String = km.chooseServerAlias(keyType, issuers, socket)
      override def chooseEngineServerAlias(
          keyType: String,
          issuers: Array[Principal],
          engine: SSLEngine,
      ): String = km.chooseEngineServerAlias(keyType, issuers, engine)
      override def getClientAliases(keyType: String, issuers: Array[Principal]): Array[String] =
        km.getClientAliases(keyType, issuers)
      override def getServerAliases(keyType: String, issuers: Array[Principal]): Array[String] =
        km.getServerAliases(keyType, issuers)
      override def getCertificateChain(alias: String): Array[X509Certificate] =
        km.getCertificateChain(alias)
      override def getPrivateKey(alias: String): java.security.PrivateKey = km.getPrivateKey(alias)
    }

  /** Trusts [[TestCertificates.caPem]], and presents `identity` (certificate, key) if given. */
  protected def tlsContext(identity: Option[(String, String)]): Resource[IO, TLSContext[IO]] =
    Resource.eval(IO {
      val trustStore = emptyKeyStore
      trustStore.setCertificateEntry("ca", certificate(TestCertificates.caPem))
      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(trustStore)

      val keyManagers = identity.map { case (cert, key) =>
        val privateKey = KeyFactory
          .getInstance("EC")
          .generatePrivate(new PKCS8EncodedKeySpec(TestCertificates.der(key).toArray))
        val keyStore = emptyKeyStore
        keyStore.setKeyEntry("identity", privateKey, password, Array(certificate(cert)))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        kmf.init(keyStore, password)
        kmf.getKeyManagers.map {
          case km: X509ExtendedKeyManager => alwaysPresent(km, "identity")
          case km => km
        }
      }

      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(keyManagers.orNull, tmf.getTrustManagers, null)
      TLSContext.Builder.forAsync[IO].fromSSLContext(sslContext)
    })

  protected def configureClient(builder: EmberClientBuilder[IO]): EmberClientBuilder[IO] =
    builder

  protected val requireClientCert: TLSParameters = TLSParameters(needClientAuth = true)

  /** Each way to require a client certificate. */
  protected val requireClientCertVariants: List[TLSParameters] = List(requireClientCert)

  protected val optionalClientCert: TLSParameters = TLSParameters(wantClientAuth = true)

  /** JSSE fails the handshake if an optional client certificate does not verify. */
  protected val acceptsUntrustedOptionalClientCert: Boolean = false

  /** The client certificate chain the server should report: the chain as the client sent it,
    * which is just the client's certificate.
    */
  protected val clientChain: List[ByteVector] =
    List(TestCertificates.der(TestCertificates.clientPem))

  protected def certificates(session: SecureSession): List[ByteVector] =
    session.X509Certificate.map(cert => ByteVector.view(cert.getEncoded))

  protected def assertSessionDetails(session: SecureSession): Unit = {
    assertNotEquals(session.sslSessionId, "")
    assertNotEquals(session.cipherSuite, "")
    assertNotEquals(session.keySize, 0)
  }
}
