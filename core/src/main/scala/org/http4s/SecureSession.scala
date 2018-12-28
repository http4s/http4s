package org.http4s

import java.security.cert.X509Certificate

final case class SecureSession(
  sslSessionId:String,
  cipherSuite: String,
  keySize: Int,
  X509Certificate: Array[X509Certificate])
