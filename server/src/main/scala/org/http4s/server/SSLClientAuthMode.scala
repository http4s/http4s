package org.http4s.server

/**
  * Client Auth mode for mTLS
  */
sealed trait SSLClientAuthMode extends Product with Serializable

object SSLClientAuthMode {
  case object NotRequested extends SSLClientAuthMode
  case object Requested extends SSLClientAuthMode
  case object Required extends SSLClientAuthMode
}
