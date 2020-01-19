package org.http4s.server.blaze

import org.http4s.server.KeyStoreBits

import scala.concurrent.duration.Duration

final case class Config(
    host: Option[String] = None,
    port: Option[Int] = None,
    responseHeaderTimeout: Option[Duration] = None,
    idleTimeout: Option[Duration] = None,
    nio2: Option[Boolean] = None,
    connectorPoolSize: Option[Int] = None,
    bufferSize: Option[Int] = None,
    webSockets: Option[Boolean] = None,
    enableHttp2: Option[Boolean] = None,
    maxRequestLineLength: Option[Int] = None,
    maxHeadersLength: Option[Int] = None,
    chunkBufferMaxSize: Option[Int] = None,
    banner: Option[List[String]] = None,
    keyStoreBits: Option[KeyStoreBits] = None
)
