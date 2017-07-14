package org.http4s
package server
package middleware

import cats.effect.Effect
import cats.implicits._
import fs2._
import org.log4s.{Logger => SLogger}
import scodec.bits.ByteVector

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  def apply[F[_]: Effect](logHeaders: Boolean, logBody: Boolean)
                         (httpService: HttpService[F]): HttpService[F] =
    ResponseLogger(logHeaders, logBody)(
      RequestLogger(logHeaders, logBody)(
        httpService
      )
    )

  def getCharsetFromContentType[F[_]](m: Message[F]): (Boolean, Option[Charset]) = {
    val init = m.charset
    val binary = m.contentType.map(_.mediaType.binary).getOrElse(false)
    (binary, init)
  }

  /**
    * Log Messages As They Pass
    */
  def messageLogPipe[F[_], A <: Message[F]](logHeaders: Boolean, logBody: Boolean)
                                           (logger: SLogger)
                                           (implicit F: Effect[F]): Pipe[F, A, A] = stream => {
    stream.flatMap { req =>
      val (binary, charset) = Logger.getCharsetFromContentType(req)
      val headers = if (logHeaders) req.headers.toList.mkString("Headers(", ", ", ")") else ""
      val bodyT: F[String] = {
        if (logBody && !binary) {
          req.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
        } else if (logBody) {
          req.body.map(ByteVector.fromByte).map(_.toHex)
        } else {
          Stream.empty.covaryAll[F, String]
        }
      }.runFoldMonoid
      Stream.eval(bodyT.flatMap(body =>
        F.delay(logger.info(s"$headers + Body: $body"))
      )) >> Stream.emit(req)
    }
  }
}
