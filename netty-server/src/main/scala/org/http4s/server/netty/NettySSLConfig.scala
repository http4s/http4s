package org.http4s.server.netty

import javax.net.ssl.SSLContext

import org.http4s.server.SSLKeyStoreSupport.StoreInfo

sealed trait NettySSLConfig

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
) extends NettySSLConfig

final case class MikuSSLContextBits(sslContext: SSLContext, clientAuth: ClientAuth)
    extends NettySSLConfig
