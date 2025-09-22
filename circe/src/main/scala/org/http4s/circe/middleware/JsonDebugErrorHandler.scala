/*
 * Copyright 2015 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.circe.middleware

import cats.data._
import cats.effect._
import cats.syntax.all._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.headers.Connection
import org.typelevel.ci._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.LoggerFactoryGen

object JsonDebugErrorHandler {

  // Can be parametric on my other PR is merged.
  def apply[F[_]: Concurrent: LoggerFactoryGen, G[_]](
      service: Kleisli[F, Request[G], Response[G]],
      redactWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
  ): Kleisli[F, Request[G], Response[G]] = {
    val serviceErrorLogger = LoggerFactory.getLoggerFromName[F](
      "org.http4s.circe.middleware.jsondebugerrorhandler.service-errors"
    )

    val messageFailureLogger = LoggerFactory.getLoggerFromName[F](
      "org.http4s.circe.middleware.jsondebugerrorhandler.message-failures"
    )

    Kleisli { req =>
      implicit def entEnc: EntityEncoder.Pure[JsonErrorHandlerResponse[G]] =
        JsonErrorHandlerResponse.entEnc[G](redactWhen)

      service
        .run(req)
        .handleErrorWith {
          case mf: MessageFailure =>
            messageFailureLogger
              .debug(mf)(
                s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
                    .getOrElse("<unknown>")}"""
              )
              .as {
                val firstResp = mf.toHttpResponse[G](req.httpVersion)
                Response[G](
                  status = firstResp.status,
                  httpVersion = firstResp.httpVersion,
                  headers = firstResp.headers.redactSensitive(redactWhen),
                ).withEntity(JsonErrorHandlerResponse[G](req, mf))
              }
          case t =>
            serviceErrorLogger
              .error(t)(
                s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
                    .getOrElse("<unknown>")}"""
              )
              .as(
                Response[G](
                  Status.InternalServerError,
                  req.httpVersion,
                  Headers(Connection.close),
                )
                  .withEntity(JsonErrorHandlerResponse[G](req, t))
              )
        }
    }
  }

  private final case class JsonErrorHandlerResponse[F[_]](
      req: Request[F],
      caught: Throwable,
  )
  private object JsonErrorHandlerResponse {
    def entEnc[F[_]](
        redactWhen: CIString => Boolean
    ): EntityEncoder.Pure[JsonErrorHandlerResponse[F]] =
      jsonEncoderOf(
        encoder(redactWhen)
      )
    def encoder[F[_]](
        redactWhen: CIString => Boolean
    ): Encoder[JsonErrorHandlerResponse[F]] =
      (a: JsonErrorHandlerResponse[F]) =>
        Json.obj(
          "request" -> encodeRequest(a.req, redactWhen),
          "throwable" -> encodeThrowable(a.caught),
        )
  }

  private def encodeRequest[F[_]](req: Request[F], redactWhen: CIString => Boolean): Json =
    Json
      .obj(
        "method" -> req.method.name.asJson,
        "uri" -> Json
          .obj(
            "scheme" -> req.uri.scheme.map(_.value.toString).asJson,
            "authority" -> req.uri.authority
              .map(auth =>
                Json
                  .obj(
                    "host" -> auth.host.toString().asJson,
                    "port" -> auth.port.asJson,
                    "user_info" -> auth.userInfo
                      .map(_.toString())
                      .asJson,
                  )
                  .dropNullValues
              )
              .asJson,
            "path" -> req.uri.path.renderString.asJson,
            "query" -> req.uri.query.multiParams.asJson,
          )
          .dropNullValues,
        "headers" -> req.headers
          .redactSensitive(redactWhen)
          .headers
          .map { h =>
            Json.obj(
              "name" -> h.name.toString.asJson,
              "value" -> h.value.asJson,
            )
          }
          .asJson,
        "path_info" -> req.pathInfo.renderString.asJson,
        "remote_address" -> req.remoteAddr.toString.asJson,
        "http_version" -> req.httpVersion.toString.asJson,
      )
      .dropNullValues

  private def encodeThrowable(a: Throwable): Json =
    Json
      .obj(
        "message" -> Option(a.getMessage).asJson,
        "stack_trace" -> Option(a.getStackTrace())
          .map(_.toList)
          .map(_.map(stackTraceElem => stackTraceElem.toString))
          .asJson,
        "localized_message" ->
          Option(a.getLocalizedMessage()).asJson,
        "cause" -> Option(a.getCause())
          .map(encodeThrowable(_))
          .asJson,
        "suppressed" -> Option(a.getSuppressed())
          .map(_.toList.map(encodeThrowable(_)))
          .asJson,
        "class_name" -> Option(a.getClass())
          .flatMap(c => Option(c.getName()))
          .asJson,
      )
      .dropNullValues
}
