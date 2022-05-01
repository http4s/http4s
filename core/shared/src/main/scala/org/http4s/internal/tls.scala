/*
 * Copyright 2013 http4s.org
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

package org.http4s.internal

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import scala.util.Try

/** Based on SSLContextFactory from jetty. */
object tls {

  /** Return X509 certificates for the session.
    *
    * @param sslSession Session from which certificate to be read
    * @return Empty array if no certificates can be read from {{{sslSession}}}
    */
  def getCertChain(sslSession: SSLSession): List[X509Certificate] =
    Try {
      val cf = CertificateFactory.getInstance("X.509")
      sslSession.getPeerCertificates.map { certificate =>
        val stream = new ByteArrayInputStream(certificate.getEncoded)
        cf.generateCertificate(stream).asInstanceOf[X509Certificate]
      }
    }.toOption.getOrElse(Array.empty[X509Certificate]).toList

  /** Given the name of a TLS/SSL cipher suite, return an int representing it effective stream
    * cipher key strength. i.e. How much entropy material is in the key material being fed into the
    * encryption routines.
    *
    * The following specs and resources were consulted for the implementation of this function:
    * IANA TLS Cipher Suites registry (IANA https://www.iana.org/assignments/tls-parameters/tls-parameters.txt),
    * RFC 2246 (The TLS Protocol Version 1.0, Appendix C), RFC 4346 (The Transport Layer Security
    * (TLS) Protocol Version 1.1, Appendix C), RFC 5246 (The Transport Layer Security (TLS) Protocol
    * Version 1.2, Appendix C), RFC 8446 (The Transport Layer Security (TLS) Protocol Version 1.3,
    * Appendix B), RFC 7539 (ChaCha20 and Poly1305 for IETF Protocols), RFC 3713 (A Description of the
    * Camellia Encryption Algorithm), RFC 5794 (A Description of the ARIA Encryption Algorithm),
    * RFC 4269 (The SEED Encryption Algorithm), draft-crypto-sm4-00 (The SM4 Block Cipher Algorithm
    * And Its Modes Of Operations), draft-smyshlyaev-tls12-gost-suites (GOST Cipher Suites for
    * Transport Layer Security (TLS) Protocol Version 1.2), and draft-smyshlyaev-tls13-gost-suites
    * (GOST Cipher Suites for Transport Layer Security (TLS) Protocol Version).
    * The following table summarizes the relevant information from the sources listed above:
    * <pre>
    *                         Effective
    *     Cipher       Type    Key Bits
    *
    *     NULL       *  Stream     0
    *     IDEA_CBC      Block    128
    *     RC2_CBC_40 *  Block     40
    *     RC4_40     *  Stream    40
    *     RC4_128       Stream   128
    *     DES40_CBC  *  Block     40
    *     DES_CBC       Block     56
    *     3DES_EDE_CBC  Block    168
    *     AES_128       Block    128
    *     AES_256       Block    256
    *     ChaCha20      Stream   256
    *     Camellia_128  Block    128
    *     Camellia_256  Block    256
    *     Aria_128      Block    128
    *     Aria_256      Block    256
    *     SEED          Block    128
    *     SM4           Block    128
    *     Kuznyechik    Block    256
    *     Magma         Block    256
    *     GOST 28147-89 Block    256
    * </pre>
    *
    * @param cipherSuite String name of the TLS cipher suite.
    * @return int indicating the effective key entropy bit-length.
    */
  def deduceKeyLength(cipherSuite: String): Int =
    if (cipherSuite == null) 0
    else if (cipherSuite.contains("_AES_256_")) 256
    else if (cipherSuite.contains("WITH_RC4_128_")) 128
    else if (cipherSuite.contains("_AES_128_")) 128
    else if (cipherSuite.contains("WITH_RC4_40_")) 40
    else if (cipherSuite.contains("WITH_3DES_EDE_CBC_")) 168
    else if (cipherSuite.contains("WITH_IDEA_CBC_")) 128
    else if (cipherSuite.contains("WITH_RC2_CBC_40_")) 40
    else if (cipherSuite.contains("WITH_DES40_CBC_")) 40
    else if (cipherSuite.contains("WITH_DES_CBC_40")) 40
    else if (cipherSuite.contains("WITH_DES_CBC_")) 56
    else if (cipherSuite.contains("_CHACHA20_")) 256
    else if (cipherSuite.contains("_WITH_CAMELLIA_128_")) 128
    else if (cipherSuite.contains("_WITH_CAMELLIA_256_")) 256
    else if (cipherSuite.contains("_WITH_ARIA_128_")) 128
    else if (cipherSuite.contains("_WITH_ARIA_256_")) 256
    else if (cipherSuite.contains("_WITH_SEED_")) 128
    else if (cipherSuite.contains("_SM4_")) 128
    else if (cipherSuite.contains("_WITH_KUZNYECHIK_")) 256
    else if (cipherSuite.contains("_WITH_MAGMA_")) 256
    else if (cipherSuite.contains("_WITH_28147_")) 256
    else 0
}
