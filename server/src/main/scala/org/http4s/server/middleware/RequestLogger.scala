package org.http4s
package server
package middleware

import fs2._
import org.log4s._
import cats.implicits._
import fs2.interop.cats._

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply(service: HttpService)(implicit strategy: Strategy): HttpService = Service.lift{ req =>
    Stream(req)
      .observe(requestLogSink)
      .evalMap[Task, Task, MaybeResponse]{req => service(req)}
      .runFoldMap(identity)
  }

  def requestLogSink: Sink[Task, Request] = stream => {
    stream
      .flatMap(_.body)
      .through(fs2.text.utf8Decode)
      .evalMap[Task, Task, Unit]{ str => Task.delay(logger.info(str)) }
  }
}
