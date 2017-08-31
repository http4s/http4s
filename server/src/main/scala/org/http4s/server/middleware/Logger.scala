package org.http4s
package server
package middleware

import fs2._
import cats.implicits._
import fs2.interop.cats._
import org.http4s.util.CaseInsensitiveString
import scodec.bits.ByteVector
import org.log4s.{Logger => SLogger}

/**
  * Simple Middleware for Logging All Requests and Responses
  */
object Logger {
  def apply(
             logHeaders: Boolean,
             logBody: Boolean,
             redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
           )(httpService: HttpService)(implicit strategy: Strategy): HttpService =
    ResponseLogger(logHeaders, logBody)(
      RequestLogger(logHeaders, logBody)(
        httpService
      )
    )


  def logMessage[A <: Message](message: A)
                              (logHeaders: Boolean,
                               logBody: Boolean,
                               redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
                              )
                              (logger: SLogger)
                              (implicit strategy: Strategy): Task[Unit] = {

    val charset = message.charset
    val isBinary = message.contentType.exists(_.mediaType.binary)
    val isJson = message.contentType.exists(mT =>
      mT.mediaType == MediaType.`application/json` || mT.mediaType == MediaType.`application/hal+json`
    )

    val isText = !isBinary || isJson

    val headers = if (logHeaders) {
      message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
    } else ""

    val bodyStream = if (logBody && isText) {
      message.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
    } else if (logBody) {
      message.body.map(ByteVector.fromByte).map(_.toHex)
    } else {
      Stream.empty[Task, String]
    }

    val bodyText = if (logBody) {
      bodyStream.fold("")(_ + _).map(text => s"""body="$text"""")
    } else {
      Stream("")
    }


    if (!logBody && !logHeaders){
      Task.now(())
    } else {
      bodyText.map(body => s"$headers $body")
        .map(text => logger.info(text))
        .run
    }
  }
}
