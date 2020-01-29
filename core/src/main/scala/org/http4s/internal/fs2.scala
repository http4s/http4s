package org.http4s.internal

import _root_.fs2.io.tls.TLSParameters
import javax.net.ssl.SSLParameters
import org.http4s.internal.CollectionCompat.CollectionConverters._

private[http4s] object fs2 {
  def toSSLParameters(tlsParameters: TLSParameters): SSLParameters = {
    val p = new SSLParameters()
    import tlsParameters._
    algorithmConstraints.foreach(p.setAlgorithmConstraints)
    // applicationProtocols.foreach(ap => p.setApplicationProtocols(ap.toArray))
    cipherSuites.foreach(cs => p.setCipherSuites(cs.toArray))
    // enableRetransmissions.foreach(p.setEnableRetransmissions)
    endpointIdentificationAlgorithm.foreach(p.setEndpointIdentificationAlgorithm)
    // maximumPacketSize.foreach(p.setMaximumPacketSize)
    protocols.foreach(ps => p.setProtocols(ps.toArray))
    serverNames.foreach(sn => p.setServerNames(sn.asJava))
    sniMatchers.foreach(sm => p.setSNIMatchers(sm.asJava))
    p.setUseCipherSuitesOrder(useCipherSuitesOrder)
    if (needClientAuth)
      p.setNeedClientAuth(needClientAuth)
    else if (wantClientAuth)
      p.setWantClientAuth(wantClientAuth)
    p
  }
}
