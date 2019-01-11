package org.http4s.server.blaze

import java.io.ByteArrayInputStream
import java.security.cert.{CertificateFactory, X509Certificate}

import javax.net.ssl.SSLSession

import scala.util.Try

/**
  * Based on SSLContextFactory from jetty.
  */
private[blaze] object SSLContextFactory {

  /**
    * Return X509 certificates for the session.
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
    }.toOption.getOrElse(Array.empty).toList

  /**
    * Given the name of a TLS/SSL cipher suite, return an int representing it effective stream
    * cipher key strength. i.e. How much entropy material is in the key material being fed into the
    * encryption routines.
    *
    * This is based on the information on effective key lengths in RFC 2246 - The TLS Protocol
    * Version 1.0, Appendix C. CipherSuite definitions:
    * <pre>
    *                         Effective
    *     Cipher       Type    Key Bits
    *
    *     NULL       * Stream     0
    *     IDEA_CBC     Block    128
    *     RC2_CBC_40 * Block     40
    *     RC4_40     * Stream    40
    *     RC4_128      Stream   128
    *     DES40_CBC  * Block     40
    *     DES_CBC      Block     56
    *     3DES_EDE_CBC Block    168
    * </pre>
    *
    * @param cipherSuite String name of the TLS cipher suite.
    * @return int indicating the effective key entropy bit-length.
    */
  def deduceKeyLength(cipherSuite: String): Int =
    if (cipherSuite == null) 0
    else if (cipherSuite.contains("WITH_AES_256_")) 256
    else if (cipherSuite.contains("WITH_RC4_128_")) 128
    else if (cipherSuite.contains("WITH_AES_128_")) 128
    else if (cipherSuite.contains("WITH_RC4_40_")) 40
    else if (cipherSuite.contains("WITH_3DES_EDE_CBC_")) 168
    else if (cipherSuite.contains("WITH_IDEA_CBC_")) 128
    else if (cipherSuite.contains("WITH_RC2_CBC_40_")) 40
    else if (cipherSuite.contains("WITH_DES40_CBC_")) 40
    else if (cipherSuite.contains("WITH_DES_CBC_")) 56
    else 0

}
