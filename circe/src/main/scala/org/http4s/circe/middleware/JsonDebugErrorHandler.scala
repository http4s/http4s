package org.http4s.circe.middleware

import cats._
import cats.effect._
import cats.data._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.headers.Connection
import org.http4s.util.CaseInsensitiveString
import org.http4s.implicits._
import org.http4s.circe._

object JsonDebugErrorHandler {
  private[this] val messageFailureLogger =
    org.log4s.getLogger("org.http4s.circe.middleware.jsondebugerrorhandler.message-failures")
  private[this] val serviceErrorLogger =
    org.log4s.getLogger("org.http4s.circe.middleware.jsondebugerrorhandler.service-errors")

  // Can be parametric on my other PR is merged.
  def apply[F[_]: Sync, G[_]: Applicative](
      service: Kleisli[F, Request[G], Response[G]],
      redactWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): Kleisli[F, Request[G], Response[G]] = Kleisli { req =>
    import cats.implicits._
    implicit def entEnc[M[_]: Applicative, N[_]] = JsonErrorHandlerResponse.entEnc[M, N](redactWhen)

    service
      .run(req)
      .handleErrorWith {
        case mf: MessageFailure =>
          messageFailureLogger.debug(mf)(
            s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}""")
          mf.inHttpResponse[F, G](req.httpVersion).map { firstResp =>
            Response[G](
              status = firstResp.status,
              httpVersion = firstResp.httpVersion,
              headers = firstResp.headers.redactSensitive(redactWhen)
            ).withEntity(JsonErrorHandlerResponse[G](req, mf))
          }
        case t =>
          serviceErrorLogger.error(t)(
            s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}"""
          )
          Response[G](
            Status.InternalServerError,
            req.httpVersion,
            Headers(
              Connection("close".ci) ::
                Nil
            ))
            .withEntity(JsonErrorHandlerResponse[G](req, t))
            .pure[F]
      }
  }

  private final case class JsonErrorHandlerResponse[F[_]](
      req: Request[F],
      caught: Throwable
  )
  private object JsonErrorHandlerResponse {
    def entEnc[F[_]: Applicative, G[_]](
        redactWhen: CaseInsensitiveString => Boolean
    ): EntityEncoder[F, JsonErrorHandlerResponse[G]] =
      jsonEncoderOf(
        Applicative[F],
        encoder(redactWhen)
      )
    def encoder[F[_]](
        redactWhen: CaseInsensitiveString => Boolean
    ): Encoder[JsonErrorHandlerResponse[F]] = new Encoder[JsonErrorHandlerResponse[F]] {
      def apply(a: JsonErrorHandlerResponse[F]): Json =
        Json.obj(
          "request" -> encodeRequest(a.req, redactWhen),
          "throwable" -> encodeThrowable(a.caught)
        )
    }
  }

  private def encodeRequest[F[_]](
      req: Request[F],
      redactWhen: CaseInsensitiveString => Boolean): Json =
    Json
      .obj(
        "method" -> req.method.name.asJson,
        "uri" -> Json
          .obj(
            "scheme" -> req.uri.scheme.map(_.value).asJson,
            "authority" -> req.uri.authority
              .map(
                auth =>
                  Json
                    .obj(
                      "host" -> auth.host.toString().asJson,
                      "port" -> auth.port.asJson,
                      "user_info" -> auth.userInfo
                        .map(_.toString())
                        .asJson
                    )
                    .dropNullValues)
              .asJson,
            "path" -> req.uri.path.asJson,
            "query" -> req.uri.query.multiParams.asJson
          )
          .dropNullValues,
        "headers" -> req.headers
          .redactSensitive(redactWhen)
          .toList
          .map(_.toRaw)
          .map { h =>
            Json.obj(
              "name" -> h.name.toString.asJson,
              "value" -> h.value.asJson
            )
          }
          .asJson,
        "path_info" -> req.pathInfo.asJson,
        "remote_address" -> req.remoteAddr.asJson,
        "http_version" -> req.httpVersion.toString().asJson
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
          .flatMap(c => Option(c.getCanonicalName()))
          .asJson
      )
      .dropNullValues
}
