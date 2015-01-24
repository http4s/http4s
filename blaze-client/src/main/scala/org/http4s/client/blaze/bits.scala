package org.http4s.client.blaze

import java.security.{NoSuchAlgorithmException, SecureRandom}
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, X509TrustManager}

private[blaze] object bits {

  /** The sslContext which will generate SSL engines for the pipeline
    * Override to provide more specific SSL managers */
  lazy val sslContext = defaultTrustManagerSSLContext()

  private class DefaultTrustManager extends X509TrustManager {
    def getAcceptedIssuers(): Array[X509Certificate] =  new Array[java.security.cert.X509Certificate](0)
    def checkClientTrusted(certs: Array[X509Certificate], authType: String) { }
    def checkServerTrusted(certs: Array[X509Certificate], authType: String) { }
  }

  private def defaultTrustManagerSSLContext(): SSLContext = try {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array(new DefaultTrustManager()), new SecureRandom())
    sslContext
  } catch {
    case e: NoSuchAlgorithmException => throw new ExceptionInInitializerError(e)
    case e: ExceptionInInitializerError => throw new ExceptionInInitializerError(e)
  }
}
