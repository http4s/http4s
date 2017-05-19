package org.http4s
package server
package middleware

import fs2._
import cats.implicits._
import fs2.interop.cats._
import scodec.bits.ByteVector
import org.log4s.{Logger => SLogger}

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  def apply(logHeaders: Boolean, logBody: Boolean)(httpService: HttpService)(implicit strategy: Strategy): HttpService =
    ResponseLogger(logHeaders, logBody)(
      RequestLogger(logHeaders, logBody)(
        httpService
      )
    )

  def getCharsetFromContentType(m: Message): (Boolean, Option[Charset]) = {
    val init = m.charset
    val binary = m.contentType.map(_.mediaType.binary).getOrElse(false)
    (binary, init)
  }

  /**
    * Log Messages As They Pass
    */
  def messageLogPipe[A <: Message](logHeaders: Boolean, logBody: Boolean)
                                  (logger: SLogger): Pipe[Task, A, A] = stream => {
    stream.flatMap { req =>
      val (binary, charset) = Logger.getCharsetFromContentType(req)
      val headers = if (logHeaders) req.headers.toList.mkString("Headers(", ", ", ")") else ""
      val bodyT: Task[String] = {
        if (logBody && !binary) {
          req.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
        } else if (logBody) {
          req.body.map(ByteVector.fromByte).map(_.toHex)
        } else {
          Stream.empty[Task, String]
        }
      }.runFoldMap(identity)
      Stream.eval(bodyT.flatMap(body =>
        Task.delay(logger.info(s"$headers + Body: $body"))
      )) >> Stream.emit(req)
    }
  }
}
