package org.http4s.client.blaze

import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.security.{NoSuchAlgorithmException, SecureRandom}
import javax.net.ssl.{SSLContext, X509TrustManager}

import org.http4s.Request
import org.http4s.blaze.pipeline.LeafBuilder
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.util.CaseInsensitiveString._

import scala.concurrent.ExecutionContext
import scalaz.\/-

trait Http1SSLSupport extends Http1Support {

  implicit protected def ec: ExecutionContext

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

  /** The sslContext which will generate SSL engines for the pipeline
    * Override to provide more specific SSL managers */
  protected lazy val sslContext = defaultTrustManagerSSLContext()

  override protected def buildPipeline(req: Request, closeOnFinish: Boolean): PipelineResult = {
    req.requestUri.scheme match {
      case Some(ci) if ci == "https".ci && req.requestUri.authority.isDefined =>
        val eng = sslContext.createSSLEngine()
        eng.setUseClientMode(true)

        val auth = req.requestUri.authority.get
        val t = new Http1ClientStage()
        val b = LeafBuilder(t).prepend(new SSLStage(eng))
        val port = auth.port.getOrElse(443)
        val address = new InetSocketAddress(auth.host.value, port)
        PipelineResult(b, t)

      case _ => super.buildPipeline(req, closeOnFinish)
    }
  }

  override protected def getAddress(req: Request): AddressResult = {
    val addr = req.requestUri.scheme match {
      case Some(ci) if ci == "https".ci && req.requestUri.authority.isDefined =>
        val auth = req.requestUri.authority.get
        val host = auth.host.value
        val port = auth.port.getOrElse(443)
        \/-(new InetSocketAddress(host, port))

      case _ => super.getAddress(req)
    }
    addr
  }
}
