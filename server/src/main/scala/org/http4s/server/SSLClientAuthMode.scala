package org.http4s.server

/**
  * Client Auth mode for mTLS
  */
@deprecated("Use methods that take fs2.io.tls.TLSParameters instead", "0.21.0-RC3")
sealed trait SSLClientAuthMode extends Product with Serializable

@deprecated("Use methods that take fs2.io.tls.TLSParameters instead", "0.21.0-RC3")
object SSLClientAuthMode {
  case object NotRequested extends SSLClientAuthMode
  case object Requested extends SSLClientAuthMode
  case object Required extends SSLClientAuthMode
}
