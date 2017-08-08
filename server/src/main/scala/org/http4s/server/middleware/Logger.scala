package org.http4s
package server
package middleware

import cats.effect.Effect
import cats.implicits._
import fs2._
import org.log4s.{Logger => SLogger}
import scala.concurrent.ExecutionContext
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

  def logMessage[F[_], A <: Message[F]](message: A)
                              (logHeaders: Boolean, logBody: Boolean)
                              (logger: SLogger)
                              (implicit executionContext: ExecutionContext = ExecutionContext.global): F[Unit] = {

    val charset = message.charset
    val binary = message.contentType.exists(_.mediaType.binary)

    val headers = if (logHeaders) message.headers.toList.mkString("Headers(", ", ", ")") else ""

    val bodyStream = if (logBody && !binary) {
      message.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
    } else if (logBody) {
      message.body.map(ByteVector.fromByte).map(_.toHex)
    } else {
      Stream.empty
    }

    val bodyText = if (logBody) {
      bodyStream.fold("")(_ + _).map(text => s"""body="$text"""")
    } else {
      Stream("")
    }


    if (!logBody && !logHeaders){
      F.pure(())
    } else {
      bodyText.map(body => s"$headers $body")
        .map(text => logger.info(text))
        .run
    }
  }
}
