package org.http4s.client.blaze

import org.http4s.headers.`User-Agent`

import scala.concurrent.duration.Duration

final case class Config(
    responseHeaderTimeout: Option[Duration] = None,
    idleTimeout: Option[Duration] = None,
    requestTimeout: Option[Duration] = None,
    connectTimeout: Option[Duration] = None,
    maxTotalConnections: Option[Int] = None,
    maxWaitQueueLimit: Option[Int] = None,
    checkEndpointAuthentication: Option[Boolean] = None,
    maxResponseLineSize: Option[Int] = None,
    maxHeaderLength: Option[Int] = None,
    maxChunkSize: Option[Int] = None,
    chunkBufferMaxSize: Option[Int] = None,
    parserMode: Option[ParserMode] = None,
    bufferSize: Option[Int] = None,
    userAgent: Option[`User-Agent`] = None
)
