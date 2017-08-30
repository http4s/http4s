package org.http4s
package client
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s._

import scala.concurrent.ExecutionContext

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply[F[_]: Effect](
                           logHeaders: Boolean,
                           logBody: Boolean,
                           redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
                           uriHeaderName: String = "uri"
           )(service: HttpService[F])
           (implicit ec: ExecutionContext = ExecutionContext.global): HttpService[F] =

    Service.lift { req =>
      if (!logBody) Logger.logMessage[F, Request[F]](addUri(req, uriHeaderName))(logHeaders, logBody)(logger) >> service(req)
      else async.unboundedQueue[F, Byte].flatMap { queue =>
        val newBody =
          Stream.eval(queue.size.get)
            .flatMap(size => queue.dequeue.take(size.toLong))

        val changedRequest = req.withBodyStream(
          req.body
            .observe(queue.enqueue)
            .onFinalize(
              Logger.logMessage[F, Request[F]](addUri(req, uriHeaderName).withBodyStream(newBody))(logHeaders, logBody, redactHeadersWhen)(logger)
            )
        )

        service(changedRequest)
      }
    }

  private def addUri[F[_] : Effect](req: Request[F], headerName: String): Request[F] = req.putHeaders(Header(headerName, req.uri.toString()))
}
