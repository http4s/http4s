package org.http4s.circe.middleware

import cats._

import cats.effect._
import cats.data._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.util.CaseInsensitiveString
import org.http4s.implicits._
import org.http4s.circe._

object JsonDebugErrorHandler {

  private[this] val messageFailureLogger =
    org.log4s.getLogger("org.http4s.circe.jsondebugerrorhandler.message-failures")
  private[this] val serviceErrorLogger =
    org.log4s.getLogger("org.http4s.circe.jsondebugerrorhandler.service-errors")

  // Can be parametric on my other PR is merged.
  def apply[F[_]](
      service: Kleisli[F, Request[F], Response[F]],
      redactWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(implicit F: Sync[F]): Kleisli[F, Request[F], Response[F]] = Kleisli { req =>
    import cats.implicits._
    implicit def entEnc[M[_]: Applicative, N[_]] = JsonErrorHandlerResponse.entEnc[M, N](redactWhen)

    service
      .run(req)
      .handleErrorWith {
        case mf: MessageFailure =>
          messageFailureLogger.debug(mf)(
            s"""Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}""")
          mf.toHttpResponse[F](req.httpVersion).map { firstResp =>
            Response[F](
              status = firstResp.status,
              httpVersion = firstResp.httpVersion,
              headers = firstResp.headers.redactSensitive(redactWhen)
            ).withEntity(JsonErrorHandlerResponse[F](req, mf))
          }
        case scala.util.control.NonFatal(t) =>
          serviceErrorLogger.error(t)(
            s"""Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr
              .getOrElse("<unknown>")}"""
          )
          Response[F](
            Status.InternalServerError,
            req.httpVersion,
            Headers(
              Connection("close".ci) ::
                `Content-Length`.zero ::
                Nil
            ))
            .withEntity(JsonErrorHandlerResponse(req, t))
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
    Json.obj(
      "method" -> req.method.name.asJson,
      "uri" -> Json.obj(
        "authority" -> req.uri.authority
          .map(
            auth =>
              Json.obj(
                "host" -> auth.host.toString().asJson,
                "port" -> auth.port.asJson,
                "user_info" -> auth.userInfo
                  .map(_.toString())
                  .asJson
              ))
          .asJson,
        "path" -> req.uri.path.asJson,
        "params" -> req.uri.params.asJson
      ),
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

  private def encodeThrowable(a: Throwable): Json =
    Json.obj(
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
}
