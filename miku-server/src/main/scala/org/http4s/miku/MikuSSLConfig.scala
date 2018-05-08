package org.http4s.miku

import javax.net.ssl.SSLContext

import org.http4s.server.SSLKeyStoreSupport.StoreInfo

sealed trait MikuSSLConfig

sealed trait ClientAuth
case object ClientAuthRequired extends ClientAuth
case object ClientAuthOptional extends ClientAuth
case object NoClientAuth extends ClientAuth

final case class MikuKeyStoreBits(
    keyStore: StoreInfo,
    keyManagerPassword: String,
    protocol: String,
    trustStore: Option[StoreInfo],
    clientAuth: ClientAuth
) extends MikuSSLConfig

final case class MikuSSLContextBits(sslContext: SSLContext, clientAuth: ClientAuth)
    extends MikuSSLConfig
