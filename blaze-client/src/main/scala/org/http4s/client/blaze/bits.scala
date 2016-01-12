package org.http4s.client.blaze

import java.security.{NoSuchAlgorithmException, SecureRandom}
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, X509TrustManager}

import java.util.concurrent._

import org.http4s.BuildInfo
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.util.threads

import scala.concurrent.duration._
import scala.math.max

private[blaze] object bits {
  // Some default objects
  val DefaultTimeout: Duration = 60.seconds
  val DefaultBufferSize: Int = 8*1024
  val DefaultUserAgent = Some(`User-Agent`(AgentProduct("http4s-blaze", Some(BuildInfo.version))))
  val ClientDefaultEC = {
    val maxThreads = max(4, (Runtime.getRuntime.availableProcessors * 1.5).ceil.toInt)
    val threadFactory = threads.threadFactory(name = (i => s"http4s-blaze-client-$i"), daemon = true)
    Executors.newFixedThreadPool(maxThreads, threadFactory)
  }

  val ClientTickWheel = new TickWheelExecutor()

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
