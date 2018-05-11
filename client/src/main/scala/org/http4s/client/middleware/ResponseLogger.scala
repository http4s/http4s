package org.http4s
package client
package middleware

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.log4s.getLogger
import scala.concurrent.ExecutionContext

/**
  * Simple middleware for logging responses as they are processed
  */
object ResponseLogger {
  private[this] val logger = getLogger

  @deprecated("Deadlocks. Use apply0 until we can break compatibility.", "0.18.11")
  def apply[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(service: HttpService[F])(
      implicit F: Effect[F],
      ec: ExecutionContext = ExecutionContext.global): HttpService[F] =
    Kleisli { req =>
      service(req).semiflatMap { response =>
        if (!logBody)
          Logger.logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(
            logger) *> F.delay(response)
        else
          async.refOf[F, Vector[Segment[Byte, Unit]]](Vector.empty[Segment[Byte, Unit]]).map {
            vec =>
              val newBody = Stream
                .eval(vec.get)
                .flatMap(v => Stream.emits(v).covary[F])
                .flatMap(c => Stream.segment(c).covary[F])

              response.copy(
                body = response.body
                // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                  .observe(_.segments.flatMap(s => Stream.eval_(vec.modify(_ :+ s))))
                  .onFinalize {
                    Logger.logMessage[F, Response[F]](response.withBodyStream(newBody))(
                      logHeaders,
                      logBody,
                      redactHeadersWhen)(logger)
                  }
              )
          }
      }
    }

  def apply0[F[_]](
      logHeaders: Boolean,
      logBody: Boolean,
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(client: Client[F])(
      implicit F: Effect[F],
      ec: ExecutionContext = ExecutionContext.global): Client[F] =
    client.copy(open = Kleisli { req =>
      client.open(req).flatMap {
        case dr @ DisposableResponse(response, _) =>
          if (!logBody)
            Logger.logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(
              logger) *> F.delay(dr)
          else
            async.refOf[F, Vector[Segment[Byte, Unit]]](Vector.empty[Segment[Byte, Unit]]).map {
              vec =>
                val newBody = Stream
                  .eval(vec.get)
                  .flatMap(v => Stream.emits(v).covary[F])
                  .flatMap(c => Stream.segment(c).covary[F])

                dr.copy(
                  response = response.copy(
                    body = response.body
                    // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                      .observe(_.segments.flatMap(s => Stream.eval_(vec.modify(_ :+ s))))),
                  dispose =
                    Logger
                      .logMessage[F, Response[F]](response.withBodyStream(newBody))(
                        logHeaders,
                        logBody,
                        redactHeadersWhen)(logger)
                      .attempt
                      .flatMap {
                        case Left(t) => F.delay(logger.error(t)("Error logging response body"))
                        case Right(()) => F.unit
                      } *> dr.dispose
                )
            }
      }
    })
}
