package org.http4s.examples.spdynetty

import org.http4s.examples.ExampleRoute
import org.http4s.util.middleware.{GZip, URITranslation}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import org.http4s.spdynetty.BogusKeystore
import java.security.KeyStore
import org.http4s.netty.spdy.SimpleSpdyServer

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
  //val route = GZip(new ExampleRoute().apply())
  val route = new ExampleRoute().apply()
  val server = SimpleSpdyServer(sslContext, 4430)(URITranslation.translateRoot("/http4s")(route))
  server.run()
}
