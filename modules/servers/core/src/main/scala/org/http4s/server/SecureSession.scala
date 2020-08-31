/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server

import java.security.cert.X509Certificate

final case class SecureSession(
    sslSessionId: String,
    cipherSuite: String,
    keySize: Int,
    X509Certificate: List[X509Certificate])

object SecureSession {
  def apply(
      sslSessionId: String,
      cipherSuite: String,
      keySize: Int,
      X509Certificate: Array[X509Certificate]): SecureSession =
    SecureSession(sslSessionId, cipherSuite, keySize, X509Certificate.toList)
}
