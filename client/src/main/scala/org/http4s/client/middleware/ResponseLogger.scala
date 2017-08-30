package org.http4s
package client
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s.getLogger

import scala.concurrent.ExecutionContext

/**
  * Simple Middleware for Logging Responses As They Are Processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  def apply[F[_]](
                   logHeaders: Boolean,
                   logBody: Boolean,
                   redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
                   uriHeaderName: String = "uri"
           )(service: HttpService[F])
           (implicit F: Effect[F], ec: ExecutionContext = ExecutionContext.global): HttpService[F] =
    Service.lift { req => service(req).flatMap {
        case Pass() => Pass.pure
        case response: Response[F] =>
          if (!logBody) {
            Logger.logMessage[F, Response[F]](addUri(response, req.uri, uriHeaderName))(logHeaders, logBody, redactHeadersWhen)(logger) >> F.delay(response)
          } else async.unboundedQueue[F, Byte].map { queue =>
            val newBody = Stream.eval(queue.size.get)
              .flatMap(size => queue.dequeue.take(size.toLong))

            response.copy(
              body = response.body
                .observe(queue.enqueue)
                .onFinalize {
                  Logger.logMessage[F, Response[F]](
                    addUri(response, req.uri, uriHeaderName).withBodyStream(newBody)
                  )(logHeaders, logBody, redactHeadersWhen)(logger)
                }
            )
          }
    }}

  private def addUri[F[_] : Effect](response: Response[F], uri: Uri, headerName: String): Response[F] = response.putHeaders(Header(headerName, uri.toString()))
}
