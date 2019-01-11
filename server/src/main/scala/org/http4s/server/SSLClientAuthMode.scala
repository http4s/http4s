package org.http4s.server

/**
  * Client Auth mode for mTLS
  */
object SSLClientAuthMode extends Enumeration {
  val NotRequested, Requested, Required = Value
}
