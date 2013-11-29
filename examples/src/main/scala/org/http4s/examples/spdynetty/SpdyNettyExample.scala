package org.http4s.examples.spdynetty

import org.http4s.netty.SimpleSpdyServer
import org.http4s.ExampleRoute
import org.http4s.util.middleware.URITranslation
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import org.http4s.spdynetty.BogusKeystore
import java.security.KeyStore

/**
 * @author Bryce Anderson
 *         Created on 11/29/13
 */
object SpdyNettyExample extends App {

  val sslContext: SSLContext = {
    val ksStream = BogusKeystore.asInputStream()
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksStream, BogusKeystore.getKeyStorePassword)

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(ks, BogusKeystore.getCertificatePassword)

    val context = SSLContext.getInstance("SSL")

    context.init(kmf.getKeyManagers(), null, null)
    context
  }

  val server = SimpleSpdyServer(sslContext, 4430)(URITranslation.translateRoot("/http4s")(new ExampleRoute().apply()))
  server.run()
}
