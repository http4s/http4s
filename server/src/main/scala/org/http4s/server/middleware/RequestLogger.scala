package org.http4s
package server
package middleware

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import cats.~>
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s._

import scala.concurrent.ExecutionContext

/**
  * Simple Middleware for Logging Requests As They Are Processed
  */
object RequestLogger {
  private[this] val logger = getLogger

  def apply[F[_] : Sync, G[_] : Effect, A](f: G ~> F, logAction: String => Unit = logger.info(_))(
    logHeaders: Boolean,
    logBody: Boolean,
    redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(@deprecatedName('service) http: Kleisli[F, Request[G], Response[G]])(
    implicit ec: ExecutionContext = ExecutionContext.global): Kleisli[F, Request[G], Response[G]] = {

    Kleisli { req: Request[G] =>
      if (!logBody) {
        f(Logger
          .logMessage[G, Request[G]](req)(logHeaders, logBody)(logAction)) *> http(req)
      }
      else {
        f(async.refOf[G, Vector[Segment[Byte, Unit]]](Vector.empty[Segment[Byte, Unit]]))
          .flatMap { vec =>

            val newBody = Stream
              .eval(vec.get)
              .flatMap(v => Stream.emits(v).covary[G])
              .flatMap(c => Stream.segment(c).covary[G])

            val changedRequest = req.withBodyStream(
              req.body
                // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                .observe(_.segments.flatMap(s => Stream.eval_(vec.modify(_ :+ s))))
            )
            val response: F[Response[G]] = http(changedRequest)
            response.attempt
              .flatMap {
                case Left(e) =>
                  f(
                    Logger.logMessage[G, Request[G]](req.withBodyStream(newBody))(
                      logHeaders,
                      logBody,
                      redactHeadersWhen)(logAction) *>
                      Sync[G].raiseError[Response[G]](e)
                  )
                case Right(resp) =>
                  f(
                    Sync[G].pure(
                      resp.withBodyStream(
                        resp.body.onFinalize(
                          Logger.logMessage[G, Request[G]](req.withBodyStream(newBody))(
                            logHeaders,
                            logBody,
                            redactHeadersWhen)(logAction)
                        )
                      )
                    )
                  )
              }
          }
      }
    }
  }
}
